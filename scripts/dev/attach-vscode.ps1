$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EnvFile = Join-Path $ProjectRoot '.env'

function Write-ErrorAndExit($msg){ Write-Host $msg -ForegroundColor Red; exit 1 }

# Check code CLI
if (-not (Get-Command code -ErrorAction SilentlyContinue)) {
    Write-Host "VS Code CLI 'code' not found. Install via Command Palette → 'Shell Command: Install 'code' command in PATH'" -ForegroundColor Yellow
    Write-ErrorAndExit "Please install 'code' and retry."
}

# Get container id for app
$cid = (& docker compose --env-file $EnvFile ps -q app 2>$null) -join ''
if (-not $cid) {
    $cid = (& docker ps -q -f "name=kb_app" 2>$null) -join ''
}
if (-not $cid) {
    Write-Host "App container not running, attempting to start it with 'docker compose up -d'..."
    try {
        & docker compose --env-file $EnvFile up -d 2>$null
        if ($LASTEXITCODE -ne 0) {
            Write-ErrorAndExit "Failed to start containers. Start with 'make dev-up' and retry."
        }
    } catch {
        Write-ErrorAndExit "Failed to start containers. Start with 'make dev-up' and retry."
    }

    $attempts = 0
    while ($attempts -lt 30) {
        $cid = (& docker ps -q -f "name=kb_app" 2>$null) -join ''
        if ($cid) { break }
        $attempts++
        Start-Sleep -Seconds 1
    }

    if (-not $cid) {
        Write-ErrorAndExit 'Timed out waiting for app container to start.'
    }
}

# Ensure container still exists
try {
    & docker inspect $cid > $null 2>&1
} catch {
    Write-ErrorAndExit "App container ($cid) no longer exists. Start it with 'make dev-up' and retry."
}

# Use full container ID if available
$full = (& docker inspect --format='{{.Id}}' $cid) -join ''
if ($full) { $idForUri = $full -replace '^sha256:' , '' } else { $idForUri = $cid }

$shortId = if ($idForUri.Length -ge 12) { $idForUri.Substring(0, 12) } else { $idForUri }
$nameUri = 'vscode-remote://attached-container+kb_app'
$shortUri = "vscode-remote://attached-container+$shortId"
$fullUri = "vscode-remote://attached-container+$idForUri"

Write-Host 'Attempting to open VS Code. Candidate URIs:'
Write-Host "  1) $nameUri"
Write-Host "  2) $shortUri"
Write-Host "  3) $fullUri"

$uris = @($nameUri, $shortUri, $fullUri)
foreach ($uri in $uris) {
    try {
        & code --folder-uri $uri 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Opened VS Code for container $cid (URI: $uri)" -ForegroundColor Green
            exit 0
        }
    } catch {
    }
}

Write-Host 'Failed to open VS Code with any container URI.' -ForegroundColor Red
Write-Host "Possible reasons: 'code' CLI not in PATH, Remote - Containers extension missing, or the container stopped." -ForegroundColor Yellow
Write-Host 'Diagnostics:' -ForegroundColor Yellow
Write-Host '  docker ps -a --filter name=kb_app' -ForegroundColor Yellow
docker ps -a --filter name=kb_app
Write-Host "  docker inspect $cid" -ForegroundColor Yellow
docker inspect $cid
Write-Host 'Manual URIs:' -ForegroundColor Yellow
Write-Host "  $fullUri"
Write-Host "  $shortUri"
Write-Host "  $nameUri"
exit 1
