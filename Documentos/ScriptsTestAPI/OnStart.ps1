<#
PROCEL Ingestion - Full E2E test script (PowerShell) [UPDATED]
Backend changed to accept from/to as STRING and parse with Instant.parse().
So we can send ISO-8601 strings directly (no URL-encoding required).

Covers:
- Rooms sync
- Sensors seed
- Pessoa create/get/update
- Presenca checkin
- Sensors ingest mock
- Medicoes: latest + list by sensor + latest + list by room
- Presenca occupancy + open presences
- Presenca checkout (by pessoa if available; fallback by presencaId)

Assumes endpoints:
  GET /api/sensors/{sensorExternalId}/medicoes?from=&to=&limit=
  GET /api/sensors/{sensorExternalId}/medicoes/latest
  GET /api/rooms/{compartimentoId}/medicoes?from=&to=&limit=
  GET /api/rooms/{compartimentoId}/medicoes/latest
#>

$ErrorActionPreference = "Stop"

# ---------------------------
# Config
# ---------------------------
$BaseUrl = "http://b8xxcv7fawgq3494xzbz97fw.187.77.58.122.sslip.io"
$RoomId = "2"
$SensorExternalId = "SII-001"

$PessoaId = "ravilon"
$PessoaEmail = "ravilon@exemplo.com"

# ---------------------------
# Helpers
# ---------------------------
function TryCall($name, [ScriptBlock]$fn) {
  try {
    $res = & $fn
    Write-Host "[OK] $name" -ForegroundColor Green
    return $res
  } catch {
    Write-Host "[FAIL] $name -> $($_.Exception.Message)" -ForegroundColor Red
    throw
  }
}

function SoftCall($name, [ScriptBlock]$fn) {
  try {
    $res = & $fn
    Write-Host "[OK] $name" -ForegroundColor Green
    return $res
  } catch {
    Write-Host "[WARN] $name -> $($_.Exception.Message)" -ForegroundColor Yellow
    return $null
  }
}

function PrintJson($title, $obj) {
  Write-Host ""
  Write-Host "=== $title ===" -ForegroundColor Cyan
  if ($null -eq $obj) { Write-Host "(null)"; return }
  $obj | ConvertTo-Json -Depth 20
}

# ISO times for queries (Instant.parse expects ISO-8601 with Z or offset)
$to = [DateTimeOffset]::UtcNow
$from10m = $to.AddMinutes(-10)

# Force format compatible with Instant.parse:
# e.g. 2026-03-04T05:00:00Z
$FromIso = $from10m.UtcDateTime.ToString("yyyy-MM-ddTHH:mm:ssZ")
$ToIso   = $to.UtcDateTime.ToString("yyyy-MM-ddTHH:mm:ssZ")

Write-Host "BaseUrl: $BaseUrl" -ForegroundColor Yellow
Write-Host "RoomId: $RoomId | SensorExternalId: $SensorExternalId | PessoaId: $PessoaId" -ForegroundColor Yellow
Write-Host "Window: from=$FromIso to=$ToIso" -ForegroundColor Yellow

# ---------------------------
# 1) Rooms sync
# ---------------------------
TryCall "POST /api/rooms/sync" {
  Invoke-RestMethod -Uri "$BaseUrl/api/rooms/sync" -Method POST
} | Out-Null

# ---------------------------
# 2) Sensors seed
# ---------------------------
TryCall "POST /api/sensors/seed/from-resource" {
  Invoke-RestMethod -Uri "$BaseUrl/api/sensors/seed/from-resource" -Method POST
} | Out-Null

# ---------------------------
# 3) Pessoa create (ignore conflict) + get + update
# ---------------------------
SoftCall "POST /api/pessoas (create - may conflict)" {
  Invoke-RestMethod -Uri "$BaseUrl/api/pessoas" -Method POST -ContentType "application/json" -Body (@{
    nome="Ravilon Aguiar"
    email=$PessoaEmail
    userId=$PessoaId
    password="123456"
    telefone="51999999999"
    matricula="MAT-001"
  } | ConvertTo-Json -Depth 10)
} | Out-Null

$pessoa = TryCall "GET /api/pessoas/$PessoaId" {
  Invoke-RestMethod -Uri "$BaseUrl/api/pessoas/$PessoaId" -Method GET
}
PrintJson "Pessoa (GET)" $pessoa

$pessoaUpd = TryCall "PUT /api/pessoas/$PessoaId (update)" {
  Invoke-RestMethod -Uri "$BaseUrl/api/pessoas/$PessoaId" -Method PUT -ContentType "application/json" -Body (@{
    nome="Ravilon A. Santos"
    telefone="51988887777"
  } | ConvertTo-Json -Depth 10)
}
PrintJson "Pessoa (PUT)" $pessoaUpd

# ---------------------------
# 4) Presenca checkin
# ---------------------------
$presenca = TryCall "POST /api/presencas/checkin" {
  Invoke-RestMethod -Uri "$BaseUrl/api/presencas/checkin" -Method POST -ContentType "application/json" -Body (@{
    pessoaId=$PessoaId
    compartimentoId=$RoomId
    source="manual"
  } | ConvertTo-Json -Depth 10)
}
PrintJson "Presenca checkin" $presenca

$presencaId = $presenca.id
if (-not $presencaId) { throw "No presenca.id returned from checkin." }

# ---------------------------
# 5) Ingest mock (generate Medicoes)
# ---------------------------
$ingestRes = TryCall "POST /api/sensors/ingest/mock" {
  Invoke-RestMethod -Uri "$BaseUrl/api/sensors/ingest/mock" -Method POST -ContentType "application/json" -Body (@{
    sensorExternalId=$SensorExternalId
    minutesBack=10
    everySeconds=10
    source="mock"
  } | ConvertTo-Json -Depth 10)
}
PrintJson "Sensors ingest mock" $ingestRes

Start-Sleep -Seconds 1

# ---------------------------
# 6) Medicoes queries
# ---------------------------
$latestSensor = TryCall "GET /api/sensors/$SensorExternalId/medicoes/latest" {
  Invoke-RestMethod -Uri "$BaseUrl/api/sensors/$SensorExternalId/medicoes/latest" -Method GET
}
PrintJson "Medicao latest (sensor)" $latestSensor

$listSensor = TryCall "GET /api/sensors/$SensorExternalId/medicoes?from&to&limit" {
  Invoke-RestMethod -Uri "$BaseUrl/api/sensors/$SensorExternalId/medicoes?from=$FromIso&to=$ToIso&limit=50" -Method GET
}
PrintJson "Medicoes list (sensor, last 10m)" $listSensor

$latestRoom = TryCall "GET /api/rooms/$RoomId/medicoes/latest" {
  Invoke-RestMethod -Uri "$BaseUrl/api/rooms/$RoomId/medicoes/latest" -Method GET
}
PrintJson "Medicao latest (room)" $latestRoom

$listRoom = TryCall "GET /api/rooms/$RoomId/medicoes?from&to&limit" {
  Invoke-RestMethod -Uri "$BaseUrl/api/rooms/$RoomId/medicoes?from=$FromIso&to=$ToIso&limit=50" -Method GET
}
PrintJson "Medicoes list (room, last 10m)" $listRoom

# ---------------------------
# 7) Presenca occupancy + open list
# ---------------------------
$ocup = TryCall "GET /api/presencas/ocupacao/compartimentos/$RoomId" {
  Invoke-RestMethod -Uri "$BaseUrl/api/presencas/ocupacao/compartimentos/$RoomId" -Method GET
}
PrintJson "Ocupacao atual" $ocup

$abertas = TryCall "GET /api/presencas/abertas/compartimentos/$RoomId" {
  Invoke-RestMethod -Uri "$BaseUrl/api/presencas/abertas/compartimentos/$RoomId" -Method GET
}
PrintJson "Presencas abertas" $abertas

# ---------------------------
# 8) Gamification summary (console) using latestRoom.valores
# ---------------------------
Write-Host ""
Write-Host "=== GAMIFICATION SUMMARY (Room $RoomId) ===" -ForegroundColor Cyan

if ($ocup) {
  Write-Host ("Ocupacao atual: {0} pessoa(s)" -f $ocup.pessoasPresentes)
}

if ($abertas) {
  $names = @($abertas | ForEach-Object { $_.pessoaNome } | Sort-Object)
  if ($names.Count -gt 0) { Write-Host ("Presentes: {0}" -f ($names -join ", ")) }
}

if ($latestRoom -and $latestRoom.valores) {
  $v = $latestRoom.valores

  $temp = $v.temperature_c
  $energyTotal = $v.energy_total_room
  $presence = $v.presence
  $acOn = $v.ac_status
  $lightOn = $v.light_status

  if ($null -ne $temp) { Write-Host ("Temp: {0} °C" -f $temp) }
  if ($null -ne $presence) { Write-Host ("Presence sensor: {0}" -f $presence) }
  if ($null -ne $energyTotal) { Write-Host ("Energy total (room): {0} kWh(?)" -f $energyTotal) }
  if ($null -ne $acOn) { Write-Host ("AC status: {0}" -f $acOn) }
  if ($null -ne $lightOn) { Write-Host ("Light status: {0}" -f $lightOn) }

  Write-Host ("Latest timestamp: {0}" -f $latestRoom.timestamp)
} else {
  Write-Host "No latest room measurement found."
}

# ---------------------------
# 9) Checkout (by pessoa if available; fallback by presencaId)
# ---------------------------
$checkoutByPessoa = SoftCall "POST /api/presencas/checkout/by-pessoa (optional)" {
  Invoke-RestMethod -Uri "$BaseUrl/api/presencas/checkout/by-pessoa" -Method POST -ContentType "application/json" -Body (@{
    pessoaId=$PessoaId
  } | ConvertTo-Json -Depth 10)
}

if ($null -eq $checkoutByPessoa) {
  $checkoutById = TryCall "POST /api/presencas/checkout (fallback)" {
    Invoke-RestMethod -Uri "$BaseUrl/api/presencas/checkout" -Method POST -ContentType "application/json" -Body (@{
      presencaId=$presencaId
    } | ConvertTo-Json -Depth 10)
  }
  PrintJson "Checkout (by presencaId)" $checkoutById
} else {
  PrintJson "Checkout (by pessoaId)" $checkoutByPessoa
}

# ---------------------------
# 10) Occupancy after checkout
# ---------------------------
$ocup2 = TryCall "GET /api/presencas/ocupacao/compartimentos/$RoomId (after checkout)" {
  Invoke-RestMethod -Uri "$BaseUrl/api/presencas/ocupacao/compartimentos/$RoomId" -Method GET
}
PrintJson "Ocupacao after checkout" $ocup2

Write-Host ""
Write-Host "✅ Full E2E test finished successfully." -ForegroundColor Green