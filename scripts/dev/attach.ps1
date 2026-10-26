param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Command = @('sh')
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$ComposeFile = Join-Path $ProjectRoot 'docker-compose.yml'
if ($Command.Count -eq 0) { $Command = @('sh') }

Write-Host 'Opening the app runtime. Edit sources in the host checkout, then rebuild the image.'
& docker compose --project-directory $ProjectRoot -f $ComposeFile exec app @Command
exit $LASTEXITCODE
