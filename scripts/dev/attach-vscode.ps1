$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EnvFile = Join-Path $ProjectRoot '.env'

function Write-ErrorAndExit($msg){ Write-Host $msg -ForegroundColor Red; exit 1 }

# Check code CLI
if (-not (Get-Command code -ErrorAction SilentlyContinue)) {
    Write-Host "VS Code CLI 'code' not found. Install via Command Palette → 'Shell Command: Install 'code' command in PATH'" -ForegroundColor Yellow
    Write-ErrorAndExit "Please install 'code' and retry."
}

# Get container id
$cid = (& docker compose --env-file $EnvFile ps -q app) -join ''
if (-not $cid) { $cid = (& docker ps -q -f "name=kb_app") -join '' }
if (-not $cid) { Write-ErrorAndExit 'App container not running. Start it with "make dev-up" first.' }

# Ensure container still exists
try {
    & docker inspect $cid > $null 2>&1
} catch {
    Write-ErrorAndExit "App container ($cid) no longer exists. Start it with 'make dev-up' and retry."
}

# Prefer full container id for URI
$full = (& docker inspect --format='{{.Id}}' $cid) -join ''
if ($full) { $idForUri = $full -replace '^sha256:' , '' } else { $idForUri = $cid }

$uri = "vscode-remote://attached-container+$idForUri/workspace"
Write-Host "Opening VS Code URI: $uri"
try {
    & code --folder-uri $uri
    Write-Host "Opened VS Code for container $cid -> /workspace" -ForegroundColor Green
} catch {
    Write-Host "Failed to open VS Code with remote URI." -ForegroundColor Yellow
    Write-Host "Possible reasons: 'code' CLI missing, Remote - Containers not installed, or the container stopped." -ForegroundColor Yellow
    Write-Host "Run these to inspect the container:" -ForegroundColor Yellow
    Write-Host "  docker ps -a --filter name=kb_app"
    Write-Host "  docker inspect $cid"
    Write-Host "Manual URI: $uri"
    exit 1
}
