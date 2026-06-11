<#
PROCEL Ingestion - API smoke test script (PowerShell)
Backend changed to accept from/to as STRING and parse with Instant.parse().
So we can send ISO-8601 strings directly (no URL-encoding required).

Covers:
- Auth login (JWT)
- Rooms sync
- Classroom schedules sync
- Sensors seed
- Pessoa create/get/update
- Missoes catalog create/list/get/update/delete
- Atividades create/list/get/update/filter/expire
- Presenca checkin
- Rules / Parameter Qualification setup
- Sensors ingest mock
- Medicoes: latest + list by sensor + latest + list by room
- Presenca occupancy + open presences
- Presenca checkout (by pessoa if available; fallback by presencaId)

Assumes endpoints:
  POST /api/missoes
  GET /api/missoes
  GET /api/missoes/{missaoId}
  PUT /api/missoes/{missaoId}
  DELETE /api/missoes/{missaoId}
  POST /api/pessoas/{pessoaId}/atividades
  GET /api/pessoas/{pessoaId}/atividades
  GET /api/pessoas/{pessoaId}/atividades/{atividadeId}
  PUT /api/pessoas/{pessoaId}/atividades/{atividadeId}
  DELETE /api/pessoas/{pessoaId}/atividades/{atividadeId}
  GET /api/sensors/{sensorExternalId}/medicoes?from=&to=&limit=
  GET /api/sensors/{sensorExternalId}/medicoes/latest
  GET /api/rooms/{compartimentoId}/medicoes?from=&to=&limit=
  GET /api/rooms/{compartimentoId}/medicoes/latest
  POST /api/rooms/aulas/sync?weekStart={yyyy-MM-dd}
  GET /api/rooms/aulas/sync/{jobId}
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
$AdminJwtToken = $null
$UserJwtToken = $null

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

function ExpectFailure($name, [ScriptBlock]$fn, [int[]]$ExpectedStatusCodes = @()) {
  $succeeded = $false
  try {
    & $fn | Out-Null
    $succeeded = $true
  } catch {
    $statusCode = $null
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $statusCode = [int]$_.Exception.Response.StatusCode
    }

    if ($ExpectedStatusCodes.Count -gt 0 -and ($null -eq $statusCode -or $ExpectedStatusCodes -notcontains $statusCode)) {
      throw "Expected HTTP status $($ExpectedStatusCodes -join ', ') but got '$statusCode'. Error: $($_.Exception.Message)"
    }

    if ($null -eq $statusCode) {
      Write-Host "[OK] $name failed as expected" -ForegroundColor Green
    } else {
      Write-Host "[OK] $name failed as expected (HTTP $statusCode)" -ForegroundColor Green
    }
  }

  if ($succeeded) {
    throw "$name assertion failed: expected failure but request succeeded."
  }
}

function AssertSmoke($name, [bool]$Condition, [string]$Message) {
  if (-not $Condition) {
    throw "$name assertion failed: $Message"
  }
  Write-Host "[OK] $name" -ForegroundColor Green
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
$ClassScheduleWeekStart = [DateTime]::Today.AddDays(-[int][DateTime]::Today.DayOfWeek).ToString("yyyy-MM-dd")

Write-Host "Target: $Target | BaseUrl: $BaseUrl" -ForegroundColor Yellow
Write-Host "RoomId: $RoomId | SensorExternalId: $SensorExternalId | TestPessoaId: $PessoaId" -ForegroundColor Yellow
Write-Host "ClassScheduleWeekStart: $ClassScheduleWeekStart" -ForegroundColor Yellow
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
$AdminJwtToken = $JwtToken
Write-Host "[OK] JWT token acquired. ExpiresAt: $($login.expiresAt)" -ForegroundColor Green

# ---------------------------
# 2) Rooms sync
# ---------------------------
TryCall "POST /api/rooms/sync" {
  InvokeApi "/api/rooms/sync" -Method POST
} | Out-Null

# ---------------------------
# 3) Classroom schedules sync
# ---------------------------
$classScheduleSync = TryCall "POST /api/rooms/aulas/sync (all rooms, async)" {
  InvokeApi "/api/rooms/aulas/sync?weekStart=$ClassScheduleWeekStart" -Method POST
}
PrintJson "Classroom schedules async job" $classScheduleSync
AssertSmoke "Classroom schedules sync returned a job" (
  -not [string]::IsNullOrWhiteSpace($classScheduleSync.jobId) -and
  @("PENDING", "RUNNING", "COMPLETED") -contains $classScheduleSync.status
) "schedule sync did not return a valid asynchronous job."

$classScheduleJob = TryCall "GET /api/rooms/aulas/sync/$($classScheduleSync.jobId)" {
  InvokeApi "/api/rooms/aulas/sync/$($classScheduleSync.jobId)" -Method GET
}
PrintJson "Classroom schedules job status" $classScheduleJob
AssertSmoke "Classroom schedules job is available" (
  "$($classScheduleJob.jobId)" -eq "$($classScheduleSync.jobId)" -and
  $classScheduleJob.status -ne "FAILED"
) "schedule sync job was not found or failed immediately."

# ---------------------------
# 4) Sensors seed
# ---------------------------
TryCall "POST /api/sensors/seed/from-resource" {
  InvokeApi "/api/sensors/seed/from-resource" -Method POST
} | Out-Null

# ---------------------------
# 5) Test pessoa create (ignore conflict) + get + update
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

$userLogin = TryCall "POST /api/auth/login (test user)" {
  InvokeApi "/api/auth/login" -Method POST -NoAuth -Body @{
    email=$PessoaEmail
    password="123456"
  }
}

$UserJwtToken = $userLogin.accessToken
if ([string]::IsNullOrWhiteSpace($UserJwtToken)) { throw "No accessToken returned from test user login." }
Write-Host "[OK] Test user JWT token acquired. ExpiresAt: $($userLogin.expiresAt)" -ForegroundColor Green

# ---------------------------
# 5) Missoes catalog + atividades by pessoa
# ---------------------------
$MissionRunId = [Guid]::NewGuid().ToString("N").Substring(0, 8)

$missao = TryCall "POST /api/missoes" {
  InvokeApi "/api/missoes" -Method POST -Body @{
    titulo="API smoke test mission $MissionRunId"
    descricao="Mission model created by ApiSmokeTest.ps1 run $MissionRunId"
    tipo="Individual"
    value=20
    ativo=$true
  }
}
PrintJson "Missao catalog create" $missao

$missaoId = $missao.id
if (-not $missaoId) { throw "No missao.id returned from create." }
AssertSmoke "POST /api/missoes returns active mission" ($missao.ativo -eq $true -and $missao.titulo -like "*$MissionRunId") "created mission does not match request."
AssertSmoke "POST /api/missoes returns Individual type" ($missao.tipo -eq "Individual") "created mission type should be Individual."
AssertSmoke "POST /api/missoes returns numeric XP value" ($missao.value -eq 20) "created mission value should be 20."

$missaoInativa = TryCall "POST /api/missoes (inactive)" {
  InvokeApi "/api/missoes" -Method POST -Body @{
    titulo="API smoke test inactive mission $MissionRunId"
    descricao="Inactive mission used to validate assignment conflict."
    tipo="Individual"
    value=15
    ativo=$false
  }
}
PrintJson "Missao inactive create" $missaoInativa

$missaoInativaId = $missaoInativa.id
if (-not $missaoInativaId) { throw "No inactive missao.id returned from create." }
AssertSmoke "POST /api/missoes inactive returns Individual type" ($missaoInativa.tipo -eq "Individual") "inactive mission type should be Individual."
AssertSmoke "POST /api/missoes inactive returns numeric XP value" ($missaoInativa.value -eq 15) "inactive mission value should be 15."

$missoes = TryCall "GET /api/missoes?ativo=true" {
  InvokeApi "/api/missoes?ativo=true" -Method GET
}
PrintJson "Missoes catalog list" $missoes
AssertSmoke "GET /api/missoes?ativo=true includes created mission" (@($missoes | Where-Object { "$($_.id)" -eq "$missaoId" }).Count -eq 1) "created active mission was not returned by ativo=true filter."

$missoesInativas = TryCall "GET /api/missoes?ativo=false" {
  InvokeApi "/api/missoes?ativo=false" -Method GET
}
PrintJson "Missoes inactive list" $missoesInativas
AssertSmoke "GET /api/missoes?ativo=false includes inactive mission" (@($missoesInativas | Where-Object { "$($_.id)" -eq "$missaoInativaId" }).Count -eq 1) "created inactive mission was not returned by ativo=false filter."

$missoesAll = TryCall "GET /api/missoes" {
  InvokeApi "/api/missoes" -Method GET
}
PrintJson "Missoes catalog list all" $missoesAll
AssertSmoke "GET /api/missoes includes active and inactive missions" (
  @($missoesAll | Where-Object { "$($_.id)" -eq "$missaoId" -or "$($_.id)" -eq "$missaoInativaId" }).Count -eq 2
) "unfiltered list did not include both missions created by this smoke test."

$missaoGet = TryCall "GET /api/missoes/$missaoId" {
  InvokeApi "/api/missoes/$missaoId" -Method GET
}
PrintJson "Missao catalog get" $missaoGet
AssertSmoke "GET /api/missoes/{missaoId} returns requested mission" ("$($missaoGet.id)" -eq "$missaoId") "GET returned a different mission id."
AssertSmoke "GET /api/missoes/{missaoId} returns Individual type" ($missaoGet.tipo -eq "Individual") "mission type should be Individual."
AssertSmoke "GET /api/missoes/{missaoId} returns numeric XP value" ($missaoGet.value -eq 20) "mission value should be 20."

$missaoUpd = TryCall "PUT /api/missoes/$missaoId" {
  InvokeApi "/api/missoes/$missaoId" -Method PUT -Body @{
    titulo="API smoke test mission updated $MissionRunId"
    descricao="Mission model updated by ApiSmokeTest.ps1 run $MissionRunId"
    tipo="Individual"
    value=25
    ativo=$true
  }
}
PrintJson "Missao catalog update" $missaoUpd
AssertSmoke "PUT /api/missoes/{missaoId} updates title" ($missaoUpd.titulo -eq "API smoke test mission updated $MissionRunId") "updated title was not returned."
AssertSmoke "PUT /api/missoes/{missaoId} keeps Individual type" ($missaoUpd.tipo -eq "Individual") "updated mission type should be Individual."
AssertSmoke "PUT /api/missoes/{missaoId} updates numeric XP value" ($missaoUpd.value -eq 25) "updated mission value should be 25."

ExpectFailure "POST /api/pessoas/$PessoaId/atividades rejects inactive missao" {
  InvokeApi "/api/pessoas/$PessoaId/atividades" -Method POST -Body @{
    missaoId=$missaoInativaId
    status="PENDENTE"
  }
} @(409)

$atividade = TryCall "POST /api/pessoas/$PessoaId/atividades" {
  InvokeApi "/api/pessoas/$PessoaId/atividades" -Method POST -Body @{
    missaoId=$missaoId
    status="PENDENTE"
    startedAt=$FromIso
  }
}
PrintJson "Atividade create" $atividade

$atividadeId = $atividade.id
if (-not $atividadeId) { throw "No atividade.id returned from create." }
AssertSmoke "POST /api/pessoas/{pessoaId}/atividades returns PENDENTE activity" ($atividade.status -eq "PENDENTE" -and "$($atividade.missaoId)" -eq "$missaoId") "created activity does not match mission/status."

ExpectFailure "POST /api/pessoas/$PessoaId/atividades rejects duplicate missao" {
  InvokeApi "/api/pessoas/$PessoaId/atividades" -Method POST -Body @{
    missaoId=$missaoId
    status="PENDENTE"
  }
} @(409)

$atividades = TryCall "GET /api/pessoas/$PessoaId/atividades" {
  InvokeApi "/api/pessoas/$PessoaId/atividades" -Method GET
}
PrintJson "Atividades list" $atividades
AssertSmoke "GET /api/pessoas/{pessoaId}/atividades includes created activity" (@($atividades | Where-Object { "$($_.id)" -eq "$atividadeId" }).Count -eq 1) "created activity was not returned."

$atividadesPendentes = TryCall "GET /api/pessoas/$PessoaId/atividades?status=PENDENTE" {
  InvokeApi "/api/pessoas/$PessoaId/atividades?status=PENDENTE" -Method GET
}
PrintJson "Atividades filter PENDENTE" $atividadesPendentes
AssertSmoke "GET /api/pessoas/{pessoaId}/atividades?status=PENDENTE includes created activity" (@($atividadesPendentes | Where-Object { "$($_.id)" -eq "$atividadeId" }).Count -eq 1) "PENDENTE filter did not return created activity."

$atividadeGet = TryCall "GET /api/pessoas/$PessoaId/atividades/$atividadeId" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/$atividadeId" -Method GET
}
PrintJson "Atividade get" $atividadeGet
AssertSmoke "GET /api/pessoas/{pessoaId}/atividades/{atividadeId} returns requested activity" ("$($atividadeGet.id)" -eq "$atividadeId") "GET returned a different activity id."

$JwtToken = $UserJwtToken
$atividadesOwnUser = TryCall "GET /api/pessoas/$PessoaId/atividades as own USUARIO" {
  InvokeApi "/api/pessoas/$PessoaId/atividades" -Method GET
}
PrintJson "Atividades list as own user" $atividadesOwnUser
AssertSmoke "USUARIO can list own atividades" (@($atividadesOwnUser | Where-Object { "$($_.id)" -eq "$atividadeId" }).Count -eq 1) "own user could not list own activity."

$atividadeOwnUser = TryCall "GET /api/pessoas/$PessoaId/atividades/$atividadeId as own USUARIO" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/$atividadeId" -Method GET
}
AssertSmoke "USUARIO can get own atividade" ("$($atividadeOwnUser.id)" -eq "$atividadeId") "own user could not get own activity."

$resumoOwnUser = TryCall "GET /api/pessoas/$PessoaId/atividades/resumo as own USUARIO" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/resumo" -Method GET
}
PrintJson "Atividades resumo as own user" $resumoOwnUser

ExpectFailure "GET /api/pessoas/admin/atividades rejects another pessoa for USUARIO" {
  InvokeApi "/api/pessoas/admin/atividades" -Method GET
} @(403)

$JwtToken = $AdminJwtToken

$atividadeAndamento = TryCall "PUT /api/pessoas/$PessoaId/atividades/$atividadeId (EM_ANDAMENTO)" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/$atividadeId" -Method PUT -Body @{
    status="EM_ANDAMENTO"
    startedAt=$FromIso
  }
}
PrintJson "Atividade update EM_ANDAMENTO" $atividadeAndamento
AssertSmoke "PUT atividade sets EM_ANDAMENTO" ($atividadeAndamento.status -eq "EM_ANDAMENTO" -and $null -ne $atividadeAndamento.startedAt) "activity was not moved to EM_ANDAMENTO with startedAt."

$atividadeUpd = TryCall "PUT /api/pessoas/$PessoaId/atividades/$atividadeId" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/$atividadeId" -Method PUT -Body @{
    status="CONCLUIDA"
    completedAt=$ToIso
  }
}
PrintJson "Atividade update CONCLUIDA" $atividadeUpd
AssertSmoke "PUT atividade sets CONCLUIDA" ($atividadeUpd.status -eq "CONCLUIDA" -and $null -ne $atividadeUpd.completedAt) "activity was not completed."

$atividadesConcluidas = TryCall "GET /api/pessoas/$PessoaId/atividades?status=CONCLUIDA" {
  InvokeApi "/api/pessoas/$PessoaId/atividades?status=CONCLUIDA" -Method GET
}
PrintJson "Atividades filter CONCLUIDA" $atividadesConcluidas
AssertSmoke "GET /api/pessoas/{pessoaId}/atividades?status=CONCLUIDA includes completed activity" (@($atividadesConcluidas | Where-Object { "$($_.id)" -eq "$atividadeId" }).Count -eq 1) "CONCLUIDA filter did not return completed activity."

$missaoExpiravel = TryCall "POST /api/missoes (expirable activity)" {
  InvokeApi "/api/missoes" -Method POST -Body @{
    titulo="API smoke test expirable mission $MissionRunId"
    descricao="Mission used by ApiSmokeTest.ps1 to validate EXPIRADA status."
    tipo="Individual"
    value=30
    ativo=$true
  }
}
PrintJson "Missao expirable create" $missaoExpiravel

$missaoExpiravelId = $missaoExpiravel.id
if (-not $missaoExpiravelId) { throw "No expirable missao.id returned from create." }

$atividadeExpiravel = TryCall "POST /api/pessoas/$PessoaId/atividades (to expire)" {
  InvokeApi "/api/pessoas/$PessoaId/atividades" -Method POST -Body @{
    missaoId=$missaoExpiravelId
    status="PENDENTE"
  }
}
PrintJson "Atividade expirable create" $atividadeExpiravel

$atividadeExpiravelId = $atividadeExpiravel.id
if (-not $atividadeExpiravelId) { throw "No expirable atividade.id returned from create." }

TryCall "DELETE /api/pessoas/$PessoaId/atividades/$atividadeExpiravelId (expires)" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/$atividadeExpiravelId" -Method DELETE
} | Out-Null

$atividadeExpirada = TryCall "GET expired atividade still exists" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/$atividadeExpiravelId" -Method GET
}
PrintJson "Atividade expired get" $atividadeExpirada
AssertSmoke "DELETE atividade marks EXPIRADA" ($atividadeExpirada.status -eq "EXPIRADA" -and $null -ne $atividadeExpirada.completedAt) "activity was not marked EXPIRADA."

$atividadesExpiradas = TryCall "GET /api/pessoas/$PessoaId/atividades?status=EXPIRADA" {
  InvokeApi "/api/pessoas/$PessoaId/atividades?status=EXPIRADA" -Method GET
}
PrintJson "Atividades filter EXPIRADA" $atividadesExpiradas
AssertSmoke "GET /api/pessoas/{pessoaId}/atividades?status=EXPIRADA includes expired activity" (@($atividadesExpiradas | Where-Object { "$($_.id)" -eq "$atividadeExpiravelId" }).Count -eq 1) "EXPIRADA filter did not return expired activity."

$atividadesResumo = TryCall "GET /api/pessoas/$PessoaId/atividades/resumo" {
  InvokeApi "/api/pessoas/$PessoaId/atividades/resumo" -Method GET
}
PrintJson "Atividades resumo" $atividadesResumo
AssertSmoke "Atividades resumo counts CONCLUIDA and EXPIRADA" ($atividadesResumo.concluidas -ge 1 -and $atividadesResumo.expiradas -ge 1) "summary did not count completed and expired activities."

TryCall "DELETE /api/missoes/$missaoInativaId" {
  InvokeApi "/api/missoes/$missaoInativaId" -Method DELETE
} | Out-Null

$missaoInativaDeprecated = TryCall "GET deprecated inactive missao still exists" {
  InvokeApi "/api/missoes/$missaoInativaId" -Method GET
}
AssertSmoke "DELETE inactive missao keeps row and sets ativo=false" ($missaoInativaDeprecated.ativo -eq $false) "inactive mission was not kept as deprecated."

TryCall "DELETE /api/missoes/$missaoExpiravelId" {
  InvokeApi "/api/missoes/$missaoExpiravelId" -Method DELETE
} | Out-Null

$missaoExpiravelDeprecated = TryCall "GET deprecated expirable missao still exists" {
  InvokeApi "/api/missoes/$missaoExpiravelId" -Method GET
}
AssertSmoke "DELETE expirable missao keeps row and sets ativo=false" ($missaoExpiravelDeprecated.ativo -eq $false) "expirable mission was not kept as deprecated."

TryCall "DELETE /api/missoes/$missaoId" {
  InvokeApi "/api/missoes/$missaoId" -Method DELETE
} | Out-Null

$missaoDeprecated = TryCall "GET deprecated missao still exists" {
  InvokeApi "/api/missoes/$missaoId" -Method GET
}
AssertSmoke "DELETE missao keeps row and sets ativo=false" ($missaoDeprecated.ativo -eq $false) "mission was not kept as deprecated."

# ---------------------------
# 6) Presenca checkin
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
# 7) DER Parameter Qualification setup
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
# 8) Ingest mock (generate Medicoes)
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
# 9) Medicoes queries
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
# 10) Presenca occupancy + open list
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
# 11) API test summary (console) using latestRoom.valores
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
# 12) Checkout (by pessoa if available; fallback by presencaId)
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
# 13) Occupancy after checkout
# ---------------------------
$ocup2 = TryCall "GET /api/presencas/ocupacao/compartimentos/$RoomId (after checkout)" {
  InvokeApi "/api/presencas/ocupacao/compartimentos/$RoomId" -Method GET
}
PrintJson "Ocupacao after checkout" $ocup2

Write-Host ""
Write-Host "API smoke test finished successfully." -ForegroundColor Green
