$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ComposeFile = Join-Path $ProjectRoot 'docker-compose.yml'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker Compose is required. See docs/DOCKER_LOCAL_RU.md for engine setup.'
}
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose is unavailable. Start the Docker engine and install the Compose plugin.'
}

Write-Host 'Building and starting the persistent local stack; first model preparation can take several minutes.'
& docker compose --project-directory $ProjectRoot -f $ComposeFile up -d --build --wait --wait-timeout 1800
exit $LASTEXITCODE
