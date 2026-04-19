<#
PROCEL Ingestion - Full E2E test script (PowerShell) [UPDATED]
Backend changed to accept from/to as STRING and parse with Instant.parse().
So we can send ISO-8601 strings directly (no URL-encoding required).

Covers:
- Auth login (JWT)
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
# Adjust BaseUrl for http://b8xxcv7fawgq3494xzbz97fw.187.77.58.122.sslip.io or http://localhost:8080
$BaseUrl = "http://b8xxcv7fawgq3494xzbz97fw.187.77.58.122.sslip.io"
$RoomId = "2"
$SensorExternalId = "SII-001"

$PessoaId = "ravilon"
$PessoaEmail = "ravilon@exemplo.com"

$AdminEmail = "admin@procel.local"
$AdminPassword = "admin123"
$JwtToken = $null

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

function InvokeApi($Path, $Method = "GET", $Body = $null, [switch]$NoAuth) {
  $headers = @{}
  if (-not $NoAuth) {
    if ([string]::IsNullOrWhiteSpace($JwtToken)) {
      throw "JWT token is empty. Run login before authenticated API calls."
    }
    $headers["Authorization"] = "Bearer $JwtToken"
  }

  $params = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    Headers = $headers
  }

  if ($null -ne $Body) {
    $params["ContentType"] = "application/json"
    $params["Body"] = ($Body | ConvertTo-Json -Depth 10)
  }

  Invoke-RestMethod @params
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
# 1) Auth login
# ---------------------------
$login = TryCall "POST /api/auth/login (admin bootstrap)" {
  InvokeApi "/api/auth/login" -Method POST -NoAuth -Body @{
    email=$AdminEmail
    password=$AdminPassword
  }
}

$JwtToken = $login.accessToken
if ([string]::IsNullOrWhiteSpace($JwtToken)) { throw "No accessToken returned from login." }
Write-Host "[OK] JWT token acquired. ExpiresAt: $($login.expiresAt)" -ForegroundColor Green

# ---------------------------
# 2) Rooms sync
# ---------------------------
TryCall "POST /api/rooms/sync" {
  InvokeApi "/api/rooms/sync" -Method POST
} | Out-Null

# ---------------------------
# 3) Sensors seed
# ---------------------------
TryCall "POST /api/sensors/seed/from-resource" {
  InvokeApi "/api/sensors/seed/from-resource" -Method POST
} | Out-Null

# ---------------------------
# 4) Pessoa create (ignore conflict) + get + update
# ---------------------------
SoftCall "POST /api/pessoas (create - may conflict)" {
  InvokeApi "/api/pessoas" -Method POST -Body @{
    nome="Ravilon Aguiar"
    email=$PessoaEmail
    userId=$PessoaId
    password="123456"
    telefone="51999999999"
    matricula="MAT-001"
    roles=@("USUARIO")
  }
} | Out-Null

$pessoa = TryCall "GET /api/pessoas/$PessoaId" {
  InvokeApi "/api/pessoas/$PessoaId" -Method GET
}
PrintJson "Pessoa (GET)" $pessoa

$pessoaUpd = TryCall "PUT /api/pessoas/$PessoaId (update)" {
  InvokeApi "/api/pessoas/$PessoaId" -Method PUT -Body @{
    nome="Ravilon A. Santos"
    telefone="51988887777"
  }
}
PrintJson "Pessoa (PUT)" $pessoaUpd

# ---------------------------
# 5) Presenca checkin
# ---------------------------
$presenca = TryCall "POST /api/presencas/checkin" {
  InvokeApi "/api/presencas/checkin" -Method POST -Body @{
    pessoaId=$PessoaId
    compartimentoId=$RoomId
    source="manual"
  }
}
PrintJson "Presenca checkin" $presenca

$presencaId = $presenca.id
if (-not $presencaId) { throw "No presenca.id returned from checkin." }

# ---------------------------
# 6) Ingest mock (generate Medicoes)
# ---------------------------
$ingestRes = TryCall "POST /api/sensors/ingest/mock" {
  InvokeApi "/api/sensors/ingest/mock" -Method POST -Body @{
    sensorExternalId=$SensorExternalId
    minutesBack=10
    everySeconds=10
    source="mock"
  }
}
PrintJson "Sensors ingest mock" $ingestRes

Start-Sleep -Seconds 1

# ---------------------------
# 7) Medicoes queries
# ---------------------------
$latestSensor = TryCall "GET /api/sensors/$SensorExternalId/medicoes/latest" {
  InvokeApi "/api/sensors/$SensorExternalId/medicoes/latest" -Method GET
}
PrintJson "Medicao latest (sensor)" $latestSensor

$listSensor = TryCall "GET /api/sensors/$SensorExternalId/medicoes?from&to&limit" {
  InvokeApi "/api/sensors/$SensorExternalId/medicoes?from=$FromIso&to=$ToIso&limit=50" -Method GET
}
PrintJson "Medicoes list (sensor, last 10m)" $listSensor

$latestRoom = TryCall "GET /api/rooms/$RoomId/medicoes/latest" {
  InvokeApi "/api/rooms/$RoomId/medicoes/latest" -Method GET
}
PrintJson "Medicao latest (room)" $latestRoom

$listRoom = TryCall "GET /api/rooms/$RoomId/medicoes?from&to&limit" {
  InvokeApi "/api/rooms/$RoomId/medicoes?from=$FromIso&to=$ToIso&limit=50" -Method GET
}
PrintJson "Medicoes list (room, last 10m)" $listRoom

# ---------------------------
# 8) Presenca occupancy + open list
# ---------------------------
$ocup = TryCall "GET /api/presencas/ocupacao/compartimentos/$RoomId" {
  InvokeApi "/api/presencas/ocupacao/compartimentos/$RoomId" -Method GET
}
PrintJson "Ocupacao atual" $ocup

$abertas = TryCall "GET /api/presencas/abertas/compartimentos/$RoomId" {
  InvokeApi "/api/presencas/abertas/compartimentos/$RoomId" -Method GET
}
PrintJson "Presencas abertas" $abertas

# ---------------------------
# 9) Gamification summary (console) using latestRoom.valores
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
# 10) Checkout (by pessoa if available; fallback by presencaId)
# ---------------------------
$checkoutByPessoa = SoftCall "POST /api/presencas/checkout/by-pessoa (optional)" {
  InvokeApi "/api/presencas/checkout/by-pessoa" -Method POST -Body @{
    pessoaId=$PessoaId
  }
}

if ($null -eq $checkoutByPessoa) {
  $checkoutById = TryCall "POST /api/presencas/checkout (fallback)" {
    InvokeApi "/api/presencas/checkout" -Method POST -Body @{
      presencaId=$presencaId
    }
  }
  PrintJson "Checkout (by presencaId)" $checkoutById
} else {
  PrintJson "Checkout (by pessoaId)" $checkoutByPessoa
}

# ---------------------------
# 11) Occupancy after checkout
# ---------------------------
$ocup2 = TryCall "GET /api/presencas/ocupacao/compartimentos/$RoomId (after checkout)" {
  InvokeApi "/api/presencas/ocupacao/compartimentos/$RoomId" -Method GET
}
PrintJson "Ocupacao after checkout" $ocup2

Write-Host ""
Write-Host "✅ Full E2E test finished successfully." -ForegroundColor Green
