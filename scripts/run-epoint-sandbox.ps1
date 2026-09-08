$ErrorActionPreference = "Stop"

$ContainerName = "turn-epoint-sandbox"
$Port = 8181

$PortInUse = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($PortInUse) {
  Write-Host "Epoint sandbox is already running:"
  Write-Host "  Dashboard: http://127.0.0.1:$Port"
  Write-Host "  Health:    http://127.0.0.1:$Port/_sandbox/health"
  return
}

$existing = docker ps -a --filter "name=^/$ContainerName$" --format "{{.Names}}"
if ($existing -eq $ContainerName) {
  docker rm $ContainerName | Out-Null
}

Write-Host "Starting dashboard Epoint sandbox..."
Write-Host "Dashboard: http://127.0.0.1:$Port"
Write-Host "API:       http://127.0.0.1:$Port/api/1"
Write-Host ""

docker run --rm `
  --name $ContainerName `
  -p "$Port`:8181" `
  --add-host=host.docker.internal:host-gateway `
  -e EPOINT_PUBLIC_BASE_URL="http://127.0.0.1:$Port" `
  ghcr.io/martian56/epoint-sandbox
