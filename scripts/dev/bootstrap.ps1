$ErrorActionPreference = 'Stop'

$Red = 'Red'
$Green = 'Green'
$Yellow = 'Yellow'
$Blue = 'Blue'

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EnvFile = Join-Path $ProjectRoot '.env'
$EnvExample = Join-Path $ProjectRoot '.env.example'

function Write-Section {
    param([string]$Text, [string]$Color = 'White')
    Write-Host $Text -ForegroundColor $Color
}

function Test-Command {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Test-DockerEngine {
    try {
        & docker info | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Set-EnvValue {
    param(
        [string]$Key,
        [string]$Value
    )

    $lines = @(Get-Content -LiteralPath $EnvFile -Encoding UTF8)
    $keyPattern = '^' + [regex]::Escape($Key) + '='
    $keyIndex = -1

    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $keyPattern) {
            $keyIndex = $i
            break
        }
    }

    if ($keyIndex -ge 0) {
        $currentValue = $lines[$keyIndex].Substring($Key.Length + 1)
        if ([string]::IsNullOrWhiteSpace($currentValue) -or $currentValue.StartsWith('change-me')) {
            $lines[$keyIndex] = "$Key=$Value"
            [System.IO.File]::WriteAllLines($EnvFile, $lines, (New-Object System.Text.UTF8Encoding($false)))
            return $true
        }

        return $false
    }

    $updatedLines = @($lines + "$Key=$Value")
    [System.IO.File]::WriteAllLines($EnvFile, $updatedLines, (New-Object System.Text.UTF8Encoding($false)))
    return $true
}

function Get-EnvValue {
    param([string]$Key)

    $content = Get-Content -LiteralPath $EnvFile -Encoding UTF8
    $match = $content | Where-Object { $_ -match '^' + [regex]::Escape($Key) + '=' } | Select-Object -First 1
    if ($null -eq $match) {
        return $null
    }

    return $match.Substring($Key.Length + 1)
}

Write-Section '═══════════════════════════════════════════════════════════' $Blue
Write-Section '  Developer Environment Bootstrap' $Blue
Write-Section '═══════════════════════════════════════════════════════════' $Blue

Write-Section "`n[1/6] Checking Docker and Docker Compose..." $Yellow
if (-not (Test-Command 'docker')) {
    Write-Section 'Docker is not installed' $Red
    Write-Host 'Please install Docker from https://www.docker.com/products/docker-desktop'
    exit 1
}

Write-Section "Docker installed: $(docker --version)" $Green
try {
    docker compose version | Out-Null
} catch {
    Write-Section 'Docker Compose is not installed or not compatible' $Red
    Write-Host 'Please ensure you have Docker Compose v2 installed'
    exit 1
}
Write-Section 'Docker Compose available' $Green

if (-not (Test-DockerEngine)) {
    Write-Section 'Docker Engine is not running or not reachable' $Red
    Write-Host 'Start Docker Desktop and wait until the engine is ready, then run make dev-up again.'
    exit 1
}
Write-Section 'Docker Engine available' $Green

Write-Section "`n[2/6] Preparing environment file..." $Yellow
if (-not (Test-Path -LiteralPath $EnvFile)) {
    if (-not (Test-Path -LiteralPath $EnvExample)) {
        Write-Section '.env.example not found' $Red
        exit 1
    }

    Write-Host 'Creating .env from .env.example...'
    Copy-Item -LiteralPath $EnvExample -Destination $EnvFile
    Write-Section '.env created' $Green
} else {
    Write-Section '.env already exists' $Green
}

Write-Section "`n[3/6] Validating and populating environment variables..." $Yellow

if ((Get-Content -LiteralPath $EnvFile -Encoding UTF8) -match '^JWT_SECRET_KEY=change-me') {
    Write-Host 'Generating secure JWT_SECRET_KEY...'
    $bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $jwtSecret = [Convert]::ToBase64String($bytes)
    Set-EnvValue -Key 'JWT_SECRET_KEY' -Value $jwtSecret | Out-Null
    Write-Section 'Generated secure JWT_SECRET_KEY' $Green
} else {
    Write-Section 'JWT_SECRET_KEY is set' $Green
}

Write-Host 'Validating required environment variables...'
$requiredVars = @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'JWT_SECRET_KEY')
foreach ($var in $requiredVars) {
    if (-not ((Get-Content -LiteralPath $EnvFile -Encoding UTF8) -match ('^' + [regex]::Escape($var) + '='))) {
        Write-Section "Missing required variable: $var" $Red
        exit 1
    }
}
Write-Section 'All required variables present' $Green

$sourceMode = Get-EnvValue -Key 'SOURCE_MODE'
if ([string]::IsNullOrWhiteSpace($sourceMode)) {
    $sourceMode = 'local'
}

if ($sourceMode -eq 'ssh') {
    Write-Section "`n[4/6] Checking SSH mode requirements..." $Yellow
    $gitRepoUrl = Get-EnvValue -Key 'GIT_REPO_URL'
    if ([string]::IsNullOrWhiteSpace($gitRepoUrl)) {
        Write-Section 'SSH mode requires GIT_REPO_URL' $Red
        exit 1
    }

    Write-Section "SSH mode configured with GIT_REPO_URL=$gitRepoUrl" $Green
} else {
    Write-Section "`n[4/6] Operating in LOCAL mode (code mounted from host)" $Yellow
}

Write-Section "`n[5/6] Starting containers..." $Yellow
Set-Location $ProjectRoot

Write-Host 'Stopping any existing containers...'
try {
    & docker compose --env-file .env down | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'docker compose down failed'
    }
} catch {
    Write-Section 'Docker Compose down failed, continuing with a clean start attempt.' $Yellow
}

Write-Host 'Building and starting containers...'
try {
    # Respect SKIP_BUILD flag to avoid pulling/building images
    $skipBuild = Get-EnvValue -Key 'SKIP_BUILD'
    if ([string]::IsNullOrWhiteSpace($skipBuild)) { $skipBuild = $env:SKIP_BUILD }
    if ($skipBuild -and (($skipBuild.ToLower() -eq 'true') -or ($skipBuild -eq '1'))) {
        Write-Section 'SKIP_BUILD enabled — verifying images and that no build steps are present...' $Blue
        $cfg = & docker compose --env-file .env config 2>$null
        if (-not $cfg) {
            Write-Section '✗ Failed to read docker compose configuration' $Red
            exit 1
        }
        $lines = $cfg -split "`n"
        $images = $lines | Where-Object { $_ -match '^\s*image:\s*' } | ForEach-Object { ($_ -replace '^\s*image:\s*','').Trim() }
        $builds = $lines | Where-Object { $_ -match '^\s*build:\s*' }
        if ($builds.Count -gt 0) {
            Write-Section '✗ Compose contains services with "build:" which require building images.' $Red
            Write-Host 'Either unset SKIP_BUILD or prebuild the required images on this host.'
            exit 1
        }
        $missing = @()
        foreach ($img in $images) {
            if ([string]::IsNullOrWhiteSpace($img)) { continue }
            try {
                & docker image inspect $img | Out-Null
                if ($LASTEXITCODE -ne 0) { $missing += $img }
            } catch {
                $missing += $img
            }
        }
        if ($missing.Count -gt 0) {
            Write-Section '✗ Missing required images locally:' $Red
            foreach ($m in $missing) { Write-Host "  - $m" }
            Write-Host 'Either pull these images or unset SKIP_BUILD to allow downloading/building.'
            exit 1
        }

        & docker compose --env-file .env up -d --no-build | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'docker compose up --no-build failed' }
        Write-Section 'Containers started (no build)' $Green
    } else {
        & docker compose --env-file .env up -d --build | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'docker compose up failed' }
        Write-Section 'Containers started successfully' $Green
    }
} catch {
    Write-Section 'Failed to start containers' $Red
    Write-Host ''
    Write-Host 'Recent docker compose output:'
    try {
        & docker compose --env-file .env logs --tail=20 app postgres
    } catch {
    }
    exit 1
}

Write-Section "`n[6/6] Verifying containers are running..." $Yellow

$appRunning = docker ps --filter 'name=kb_app' --filter 'status=running' | Select-String -Quiet 'kb_app'
if (-not $appRunning) {
    Write-Section 'Application container is not running' $Red
    Write-Host ''
    Write-Host 'Container status:'
    docker ps -a --filter 'name=kb_' --format 'table {{.Names}}	{{.Status}}'
    Write-Host ''
    Write-Host 'Recent logs from app:'
    try {
        & docker compose --env-file .env logs --tail=30 app
    } catch {
    }
    exit 1
}
Write-Section 'Application container is running' $Green

$postgresRunning = docker ps --filter 'name=kb_postgres' --filter 'status=running' | Select-String -Quiet 'kb_postgres'
if (-not $postgresRunning) {
    Write-Section 'PostgreSQL container is not running' $Red
    exit 1
}
Write-Section 'PostgreSQL container is running' $Green

Write-Host ''
Write-Section '═══════════════════════════════════════════════════════════' $Blue
Write-Section '✓ CONTAINERS STARTED!' $Green
Write-Section '═══════════════════════════════════════════════════════════' $Blue
Write-Host ''
Write-Section 'Note: Waiting for SSH git fetch / application startup...' $Yellow
Write-Host 'Check logs with: make dev-logs'
Write-Host ''
Write-Section 'Useful commands:' $Green
Write-Host '  View logs:       make dev-logs'
Write-Host '  Stop containers: make dev-down'
Write-Host '  Access shell:    make docker-shell'
Write-Host ''

# If AUTO_OPEN_SHELL is enabled in .env or environment, open interactive shell in /workspace
$autoShell = Get-EnvValue -Key 'AUTO_OPEN_SHELL'
if ([string]::IsNullOrWhiteSpace($autoShell)) { $autoShell = $env:AUTO_OPEN_SHELL }
if ($autoShell -and (($autoShell.ToLower() -eq 'true') -or ($autoShell -eq '1'))) {
    Write-Section 'AUTO_OPEN_SHELL enabled — opening shell in app container (/workspace)...' $Blue
    & docker compose --env-file .env exec app sh -c "cd /workspace && exec sh"
}

exit 0
