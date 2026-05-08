param(
    [string]$Shell = 'sh'
)

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EnvFile = Join-Path $ProjectRoot '.env'

# Check container running
$running = & docker compose --env-file $EnvFile ps --services --filter "status=running" 2>$null | Where-Object { $_ -eq 'app' }
if (-not $running) {
    Write-Host 'Error: app container not running. Start it with "make dev-up" or "docker compose up -d"' -ForegroundColor Red
    exit 1
}

# Exec into container and cd /workspace
& docker compose --env-file $EnvFile exec app sh -c "cd /workspace && exec $Shell"
