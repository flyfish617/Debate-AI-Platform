param(
  [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $Root "backend"
$LogDir = Join-Path $BackendDir "logs"
$OutLog = Join-Path $LogDir "backend-run.out.log"
$ErrLog = Join-Path $LogDir "backend-run.err.log"

$listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
  Select-Object -ExpandProperty OwningProcess -Unique

foreach ($processId in $listeners) {
  Stop-Process -Id $processId -Force
}

if ($listeners) {
  Start-Sleep -Seconds 2
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$process = Start-Process `
  -FilePath "mvn.cmd" `
  -ArgumentList @("spring-boot:run", "-Dspring-boot.run.profiles=local") `
  -WorkingDirectory $BackendDir `
  -WindowStyle Hidden `
  -RedirectStandardOutput $OutLog `
  -RedirectStandardError $ErrLog `
  -PassThru

Write-Host "Backend starting with PID $($process.Id)"
Write-Host "Logs:"
Write-Host "  $OutLog"
Write-Host "  $ErrLog"
