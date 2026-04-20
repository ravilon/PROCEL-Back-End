<#
PROCEL Ingestion - API smoke test script (PowerShell)
Backend changed to accept from/to as STRING and parse with Instant.parse().
So we can send ISO-8601 strings directly (no URL-encoding required).

Covers:
- Auth login (JWT)
- Rooms sync
- Sensors seed
- Pessoa create/get/update
- Presenca checkin
- Rules / Parameter Qualification setup
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

param(
  [string]$Target = $env:PROCEL_API_TARGET,

  [string]$BaseUrlOverride = $env:PROCEL_API_BASE_URL
)

$ErrorActionPreference = "Stop"

# ---------------------------
# Config
# ---------------------------
$ApiTargets = @{
  prod = "https://procel.servehttp.com"
  local = "http://localhost:8080"
}

if ([string]::IsNullOrWhiteSpace($Target)) {
  $Target = "prod"
}

if (-not $ApiTargets.ContainsKey($Target)) {
  throw "Invalid Target '$Target'. Use one of: $($ApiTargets.Keys -join ', ')"
}

if (-not [string]::IsNullOrWhiteSpace($BaseUrlOverride)) {
  $BaseUrl = $BaseUrlOverride.TrimEnd("/")
} else {
  $BaseUrl = $ApiTargets[$Target]
}

$RoomId = "2"
$SensorExternalId = "SII-001"
$SensorTipoNome = "SII_SMART"
$RuleParametroNome = "temperature_c"

$PessoaId = "api-test-user"
$PessoaEmail = "api-test-user@procel.local"

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

Write-Host "Target: $Target | BaseUrl: $BaseUrl" -ForegroundColor Yellow
Write-Host "RoomId: $RoomId | SensorExternalId: $SensorExternalId | TestPessoaId: $PessoaId" -ForegroundColor Yellow
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
# 4) Test pessoa create (ignore conflict) + get + update
# ---------------------------
SoftCall "POST /api/pessoas (create test user - may conflict)" {
  InvokeApi "/api/pessoas" -Method POST -Body @{
    nome="API Test User"
    email=$PessoaEmail
    userId=$PessoaId
    password="123456"
    telefone="51000000000"
    matricula="TEST-API-001"
    roles=@("USUARIO")
  }
} | Out-Null

$pessoa = TryCall "GET /api/pessoas/$PessoaId" {
  InvokeApi "/api/pessoas/$PessoaId" -Method GET
}
PrintJson "Pessoa (GET)" $pessoa

$pessoaUpd = TryCall "PUT /api/pessoas/$PessoaId (update)" {
  InvokeApi "/api/pessoas/$PessoaId" -Method PUT -Body @{
    nome="API Test User Updated"
    telefone="51000000001"
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
    source="api-test"
  }
}
PrintJson "Presenca checkin" $presenca

$presencaId = $presenca.id
if (-not $presencaId) { throw "No presenca.id returned from checkin." }

# ---------------------------
# 6) DER Parameter Qualification setup
# ---------------------------
# The API enforces one active/scheduled rule per sensor + ParametroDef.
# Reuse existing API test group/rule/link so this smoke test can run more than once
# against the same database without creating conflicting qualifications.
$paramDefs = TryCall "GET /api/rules/parameter-defs?tipoNome=$SensorTipoNome" {
  InvokeApi "/api/rules/parameter-defs?tipoNome=$SensorTipoNome" -Method GET
}
PrintJson "Parameter defs ($SensorTipoNome)" $paramDefs

$ruleParam = $paramDefs | Where-Object { $_.nome -eq $RuleParametroNome } | Select-Object -First 1
if ($null -eq $ruleParam -or [string]::IsNullOrWhiteSpace($ruleParam.id)) {
  throw "ParametroDef not found for tipoNome=$SensorTipoNome nome=$RuleParametroNome"
}

$ruleGroupName = "API test DER - $SensorExternalId - $RuleParametroNome"
$groups = TryCall "GET /api/rules/groups" {
  InvokeApi "/api/rules/groups" -Method GET
}
$ruleGroup = $groups | Where-Object { $_.nome -eq $ruleGroupName } | Select-Object -First 1
if ($null -eq $ruleGroup -or [string]::IsNullOrWhiteSpace($ruleGroup.id)) {
  $ruleGroup = TryCall "POST /api/rules/groups" {
    InvokeApi "/api/rules/groups" -Method POST -Body @{
      nome=$ruleGroupName
      descricao="API test rule group created by ApiSmokeTest.ps1"
      ativo=$true
    }
  }
} else {
  Write-Host "[OK] Reusing DER rule group $($ruleGroup.id)" -ForegroundColor Green
}
if ($null -eq $ruleGroup -or [string]::IsNullOrWhiteSpace($ruleGroup.id)) {
  throw "DER rule group setup failed: group id is empty."
}
PrintJson "DER rule group" $ruleGroup

$groupRules = TryCall "GET /api/rules/groups/$($ruleGroup.id)/rules" {
  InvokeApi "/api/rules/groups/$($ruleGroup.id)/rules" -Method GET
}
$rule = $groupRules | Where-Object { $_.parametroDefId -eq $ruleParam.id -and $_.ativo } | Select-Object -First 1
if ($null -eq $rule -or [string]::IsNullOrWhiteSpace($rule.id)) {
  $rule = TryCall "POST /api/rules/groups/$($ruleGroup.id)/rules" {
    InvokeApi "/api/rules/groups/$($ruleGroup.id)/rules" -Method POST -Body @{
      parametroDefId=$ruleParam.id
      nome="API test temperature qualification"
      descricao="API test rule: generated temperature above zero"
      operador="GT"
      valorNumeric1=0
      resultado="ALERTA"
      severidade=2
      prioridade=100
      ativo=$true
    }
  }
} else {
  Write-Host "[OK] Reusing DER parameter rule $($rule.id)" -ForegroundColor Green
}
if ($null -eq $rule -or [string]::IsNullOrWhiteSpace($rule.id)) {
  throw "DER parameter rule setup failed: rule id is empty."
}
PrintJson "DER parameter rule" $rule

$sensorRuleLinks = TryCall "GET /api/rules/sensors/$SensorExternalId/groups" {
  InvokeApi "/api/rules/sensors/$SensorExternalId/groups" -Method GET
}
$sensorRuleLink = $sensorRuleLinks | Where-Object { $_.grupoRegraId -eq $ruleGroup.id -and $_.status -eq "ATIVO" } | Select-Object -First 1
if ($null -eq $sensorRuleLink -or [string]::IsNullOrWhiteSpace($sensorRuleLink.id)) {
  $sensorRuleLink = TryCall "POST /api/rules/sensors/$SensorExternalId/groups" {
    InvokeApi "/api/rules/sensors/$SensorExternalId/groups" -Method POST -Body @{
      grupoRegraId=$ruleGroup.id
      status="ATIVO"
    }
  }
} else {
  Write-Host "[OK] Reusing DER sensor-rule link $($sensorRuleLink.id)" -ForegroundColor Green
}
if ($null -eq $sensorRuleLink -or [string]::IsNullOrWhiteSpace($sensorRuleLink.id)) {
  throw "DER sensor-rule link setup failed: link id is empty."
}
PrintJson "DER sensor-rule link" $sensorRuleLink

# ---------------------------
# 7) Ingest mock (generate Medicoes)
# ---------------------------
$ingestRes = TryCall "POST /api/sensors/ingest/mock" {
  InvokeApi "/api/sensors/ingest/mock" -Method POST -Body @{
    sensorExternalId=$SensorExternalId
    minutesBack=10
    everySeconds=10
    source="api-test"
  }
}
PrintJson "Sensors ingest mock" $ingestRes

Start-Sleep -Seconds 1

# ---------------------------
# 8) Medicoes queries
# ---------------------------
$latestSensor = TryCall "GET /api/sensors/$SensorExternalId/medicoes/latest" {
  InvokeApi "/api/sensors/$SensorExternalId/medicoes/latest" -Method GET
}
PrintJson "Medicao latest (sensor)" $latestSensor

$temperatureQualifications = @()
if ($latestSensor -and $latestSensor.qualificacoes -and $latestSensor.qualificacoes.temperature_c) {
  $temperatureQualifications = @($latestSensor.qualificacoes.temperature_c)
}
if ($temperatureQualifications.Count -lt 1) {
  throw "DER Parameter Qualification failed: latest sensor measurement has no qualification for temperature_c."
}
Write-Host "[OK] DER Parameter Qualification generated for temperature_c ($($temperatureQualifications.Count) result(s))." -ForegroundColor Green

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
# 9) Presenca occupancy + open list
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
# 10) API test summary (console) using latestRoom.valores
# ---------------------------
Write-Host ""
Write-Host "=== API TEST SUMMARY (Room $RoomId) ===" -ForegroundColor Cyan

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

  if ($null -ne $temp) { Write-Host ("Temp: {0} C" -f $temp) }
  if ($null -ne $presence) { Write-Host ("Presence sensor: {0}" -f $presence) }
  if ($null -ne $energyTotal) { Write-Host ("Energy total (room): {0} kWh(?)" -f $energyTotal) }
  if ($null -ne $acOn) { Write-Host ("AC status: {0}" -f $acOn) }
  if ($null -ne $lightOn) { Write-Host ("Light status: {0}" -f $lightOn) }

  Write-Host ("Latest timestamp: {0}" -f $latestRoom.timestamp)
} else {
  Write-Host "No latest room measurement found."
}

# ---------------------------
# 11) Checkout (by pessoa if available; fallback by presencaId)
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
# 12) Occupancy after checkout
# ---------------------------
$ocup2 = TryCall "GET /api/presencas/ocupacao/compartimentos/$RoomId (after checkout)" {
  InvokeApi "/api/presencas/ocupacao/compartimentos/$RoomId" -Method GET
}
PrintJson "Ocupacao after checkout" $ocup2

Write-Host ""
Write-Host "API smoke test finished successfully." -ForegroundColor Green
