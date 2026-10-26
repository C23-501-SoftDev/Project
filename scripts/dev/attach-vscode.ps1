$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if (-not (Get-Command code -ErrorAction SilentlyContinue)) {
    throw 'VS Code CLI was not found. Install its code command or open this checkout in your editor.'
}

Write-Host 'Opening the local source checkout. Rebuild with docker compose build app after edits.'
& code $ProjectRoot
exit $LASTEXITCODE
