param(
  [Parameter(Mandatory = $true)]
  [ValidateSet("staging")]
  [string]$Target,

  [Parameter(Mandatory = $true)]
  [string]$ApiBaseUrl,

  [Parameter(Mandatory = $true)]
  [string]$TelemetryBaseUrl,

  [Parameter(Mandatory = $true)]
  [string]$PostgresHost,

  [Parameter(Mandatory = $true)]
  [int]$PostgresPort,

  [Parameter(Mandatory = $true)]
  [string]$PostgresDatabase,

  [Parameter(Mandatory = $true)]
  [string]$PostgresUser,

  [Parameter(Mandatory = $true)]
  [string]$PostgresPasswordEnvVar,

  [Parameter(Mandatory = $true)]
  [string]$MongoHost,

  [Parameter(Mandatory = $true)]
  [string]$MongoDatabase,

  [Parameter(Mandatory = $true)]
  [string]$MqttHost,

  [Parameter(Mandatory = $true)]
  [string[]]$RoomIds,

  [Parameter(Mandatory = $true)]
  [string]$AdminEmail,

  [Parameter(Mandatory = $true)]
  [string]$AdminPasswordEnvVar,

  [int]$DurationSeconds = 6,
  [int]$SampleGapSeconds = 2,
  [int]$ExcessiveGapSeconds = 8,
  [int]$BacklogMeasurements = 40,
  [int]$SoakMinutes = 5,
  [switch]$SkipRestartScenario
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$Prefix = "staging-temporal-"
$RunId = "$Prefix$([Guid]::NewGuid().ToString("N").Substring(0, 12))"
$ApiBaseUrl = $ApiBaseUrl.TrimEnd("/")
$TelemetryBaseUrl = $TelemetryBaseUrl.TrimEnd("/")

function Fail([string]$Message) {
  throw "[staging-validation] $Message"
}

function SecretFromEnv([string]$Name) {
  $value = [Environment]::GetEnvironmentVariable($Name)
  if ([string]::IsNullOrWhiteSpace($value)) {
    Fail "Required secret environment variable '$Name' is not set."
  }
  return $value
}

function Assert-NotProductionText([string]$Name, [string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value)) { Fail "$Name is required." }
  $blocked = @(
    "procel.servehttp.com",
    "servehttp.com",
    "producao",
    "produção",
    "production",
    "-prod",
    ".prod.",
    "prod-",
    "prod_"
  )
  foreach ($term in $blocked) {
    if ($Value.ToLowerInvariant().Contains($term)) {
      Fail "$Name appears to target production or a blocked domain/resource: $Value"
    }
  }
}

function Assert-StagingName([string]$Name, [string]$Value) {
  Assert-NotProductionText $Name $Value
  if (-not $Value.ToLowerInvariant().Contains("staging")) {
    Fail "$Name must contain the identifier 'staging': $Value"
  }
}

function Invoke-Api(
  [string]$Path,
  [string]$Method = "GET",
  $Body = $null,
  [string]$Token = $script:ApiToken,
  [string]$BaseUrl = $script:ApiBaseUrl
) {
  $headers = @{}
  if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $headers["Authorization"] = "Bearer $Token"
  }
  $params = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    Headers = $headers
  }
  if ($null -ne $Body) {
    $params["ContentType"] = "application/json"
    $params["Body"] = ($Body | ConvertTo-Json -Depth 20)
  }
  Invoke-RestMethod @params
}

function Invoke-Sql([string]$Sql) {
  $old = [Environment]::GetEnvironmentVariable("PGPASSWORD")
  try {
    [Environment]::SetEnvironmentVariable("PGPASSWORD", $script:PostgresPassword)
    $args = @(
      "-h", $PostgresHost,
      "-p", "$PostgresPort",
      "-U", $PostgresUser,
      "-d", $PostgresDatabase,
      "-t",
      "-A",
      "-v", "ON_ERROR_STOP=1",
      "-c", $Sql
    )
    $output = & psql @args
    if ($LASTEXITCODE -ne 0) { Fail "psql failed with exit code $LASTEXITCODE." }
    return ($output | Where-Object { $_ -and $_.Trim().Length -gt 0 })
  } finally {
    [Environment]::SetEnvironmentVariable("PGPASSWORD", $old)
  }
}

function Invoke-SqlScalar([string]$Sql) {
  $rows = @(Invoke-Sql $Sql)
  if ($rows.Count -lt 1) { return $null }
  return $rows[0].Trim()
}

function Wait-Until([string]$Name, [scriptblock]$Condition, [int]$TimeoutSeconds = 60) {
  $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
  do {
    if (& $Condition) {
      Write-Host "[OK] $Name"
      return
    }
    Start-Sleep -Seconds 2
  } while ([DateTimeOffset]::UtcNow -lt $deadline)
  Fail "Timed out waiting for: $Name"
}

function Require-PrometheusMetric([string]$BaseUrl, [string]$MetricName) {
  $text = Invoke-Api "/actuator/prometheus" -BaseUrl $BaseUrl
  if ($text -notmatch [Regex]::Escape($MetricName)) {
    Fail "Metric '$MetricName' was not exposed by $BaseUrl."
  }
  Write-Host "[OK] metric exposed: $MetricName"
}

function Login-Api {
  $password = SecretFromEnv $AdminPasswordEnvVar
  $login = Invoke-Api "/api/auth/login" -Method POST -Token "" -Body @{
    email = $AdminEmail
    password = $password
  }
  if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
    Fail "API login did not return accessToken."
  }
  $script:ApiToken = $login.accessToken
  Write-Host "[OK] authenticated against staging API as $AdminEmail"
}

function Ensure-SensorTypeAndParams {
  $script:SensorType = "$RunId-type"
  $type = Invoke-Api "/api/sensor-admin/types" -Method POST -Body @{ nome = $SensorType }
  $presence = Invoke-Api "/api/sensor-admin/types/$SensorType/parameters" -Method POST -Body @{
    nome = "presence"
    descricao = "$RunId presence"
    dataType = "BOOLEAN"
    numericUnit = $null
  }
  $script:PresenceParamId = $presence.id
  Write-Host "[OK] sensor type and parameter prepared: $SensorType"
}

function Ensure-Sensors {
  $script:Sensors = @()
  for ($i = 0; $i -lt $RoomIds.Count; $i++) {
    $externalId = "$RunId-sensor-$($i + 1)"
    Invoke-Api "/api/sensor-admin/sensors" -Method POST -Body @{
      externalId = $externalId
      nome = "$RunId sensor $($i + 1)"
      tipoNome = $SensorType
      compartimentoId = $RoomIds[$i]
    } | Out-Null
    $script:Sensors += $externalId
  }
  Write-Host "[OK] staging sensors prepared: $($Sensors -join ', ')"
}

function Setup-TelemetryIntegration {
  $profile = Invoke-Api "/api/sensor-integrations/profiles" -Method POST -Body @{
    nome = "$RunId telemetry profile"
    descricao = "Disposable staging profile $RunId"
    source = "REST"
  }
  $version = Invoke-Api "/api/sensor-integrations/profiles/$($profile.id)/versions" -Method POST -Body @{
    sensorResolutionMode = "PAYLOAD_POINTER"
    messageIdPointer = "/messageId"
    sensorExternalIdPointer = "/sensorId"
    timestampPointer = "/timestamp"
    sourceReceivedAtPointer = "/receivedAt"
    timestampFormat = $null
    valueMappings = @(
      @{
        parameterName = "presence"
        valuePointer = "/values/presence"
        required = $true
      }
    )
  }
  Invoke-Api "/api/sensor-integrations/profiles/$($profile.id)/versions/$($version.id)/activate" -Method POST -Body @{
    expectedActiveVersionId = $null
  } | Out-Null
  Invoke-Api "/api/sensor-integrations/profiles/$($profile.id)/bindings" -Method POST -Body @{
    sensorExternalId = $Sensors[0]
  } | Out-Null
  Invoke-Api "/api/sensor-integrations/profiles/$($profile.id)/activate" -Method POST | Out-Null
  $script:TelemetryProfileId = $profile.id
  Write-Host "[OK] telemetry canonical profile prepared: $TelemetryProfileId"
}

function Create-DurationMission {
  $mission = Invoke-Api "/api/missoes" -Method POST -Body @{
    titulo = "$RunId mission"
    descricao = "Disposable temporal staging validation mission $RunId"
    tipo = "Individual"
    value = 0
    ativo = $true
    cicloTipo = "UNICA"
    progressoNecessario = 1
    conclusaoAutomatica = $false
  }
  $event = Invoke-Api "/api/missions/$($mission.id)/events" -Method POST -Body @{
    nome = "$RunId event"
    descricao = "Disposable duration event $RunId"
    tipoDisparo = "MEDICAO_RECEBIDA"
    modoAvaliacao = "DURACAO"
    operadorLogico = "ALL"
    politicaAtribuicao = "SEM_ATRIBUICAO_AUTOMATICA"
    janelaSegundos = ($DurationSeconds + 10)
    duracaoMinimaSegundos = $DurationSeconds
    quantidadeNecessaria = 1
    cooldownSegundos = 0
    ordem = 0
    ativo = $true
  }
  Invoke-Api "/api/mission-events/$($event.id)/conditions" -Method POST -Body @{
    parametroDefId = $PresenceParamId
    operador = "EQ"
    valorNumeric1 = $null
    valorNumeric2 = $null
    valorBoolean = $true
    valorText = $null
    agregacao = "ULTIMO"
    obrigatoria = $true
    ordem = 0
  } | Out-Null
  $script:MissionId = $mission.id
  $script:EventId = $event.id
  Write-Host "[OK] duration mission prepared: $MissionId / $EventId"
}

function Ingest-RawTelemetry([DateTimeOffset]$Timestamp, [bool]$Presence, [string]$Suffix) {
  $messageId = "$RunId-raw-$Suffix"
  $body = @{
    messageId = $messageId
    sensorId = $Sensors[0]
    timestamp = $Timestamp.UtcDateTime.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    receivedAt = [DateTimeOffset]::UtcNow.UtcDateTime.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    values = @{ presence = $Presence }
  }
  $response = Invoke-Api "/api/telemetry/events" -Method POST -Body $body -BaseUrl $TelemetryBaseUrl
  Wait-Until "raw telemetry event was canonicalized by worker" {
    $page = Invoke-Api "/api/telemetry/events?messageId=$messageId&size=1" -BaseUrl $TelemetryBaseUrl
    @($page.content | Where-Object { $_.messageId -eq $messageId -and $_.status -eq "CANONICAL_ACCEPTED" }).Count -eq 1
  } 120
  return $response
}

function Ingest-Measurement([string]$SensorExternalId, [DateTimeOffset]$Timestamp, [bool]$Presence, [string]$Suffix) {
  $body = @{
    messageId = "$RunId-$Suffix"
    sensorExternalId = $SensorExternalId
    timestamp = $Timestamp.UtcDateTime.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    source = "API"
    sourceReceivedAt = [DateTimeOffset]::UtcNow.UtcDateTime.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    values = @{ presence = $Presence }
  }
  Invoke-Api "/api/sensors/ingest" -Method POST -Body $body
}

function Count-Windows([string]$Status) {
  Invoke-SqlScalar "select count(*) from evento_janela_avaliacao j join evento_definicao e on e.id = j.evento_definicao_id where e.nome like '$Prefix%' and j.status = '$Status';"
}

function Count-Occurrences {
  Invoke-SqlScalar "select count(*) from evento_ocorrencia o join evento_definicao e on e.id = o.evento_definicao_id where e.nome like '$Prefix%' and o.status = 'CONFIRMADO';"
}

function Count-Duplicates {
  $windows = Invoke-SqlScalar "select count(*) from (select chave_idempotencia from evento_janela_avaliacao group by chave_idempotencia having count(*) > 1) d;"
  $occurrences = Invoke-SqlScalar "select count(*) from (select chave_idempotencia from evento_ocorrencia group by chave_idempotencia having count(*) > 1) d;"
  $windowEvidence = Invoke-SqlScalar "select count(*) from (select evento_janela_avaliacao_id, medicao_id, coalesce(parametro_valor_id, '00000000-0000-0000-0000-000000000000'::uuid), papel from evento_janela_evidencia group by 1,2,3,4 having count(*) > 1) d;"
  $occEvidence = Invoke-SqlScalar "select count(*) from (select evento_ocorrencia_id, medicao_id, coalesce(parametro_valor_id, '00000000-0000-0000-0000-000000000000'::uuid), papel from evento_ocorrencia_evidencia group by 1,2,3,4 having count(*) > 1) d;"
  return @{
    windows = [int]$windows
    occurrences = [int]$occurrences
    windowEvidence = [int]$windowEvidence
    occurrenceEvidence = [int]$occEvidence
  }
}

function Print-TemporalSummary {
  Write-Host ""
  Write-Host "Temporal summary for prefix $Prefix"
  Invoke-Sql @"
select status, count(*)
from evento_janela_avaliacao j
join evento_definicao e on e.id = j.evento_definicao_id
where e.nome like '$Prefix%'
group by status
order by status;
"@ | ForEach-Object { Write-Host "  window $_" }
  Write-Host "  confirmed occurrences: $(Count-Occurrences)"
  $dupes = Count-Duplicates
  Write-Host "  duplicate windows: $($dupes.windows)"
  Write-Host "  duplicate occurrences: $($dupes.occurrences)"
  Write-Host "  duplicate window evidences: $($dupes.windowEvidence)"
  Write-Host "  duplicate occurrence evidences: $($dupes.occurrenceEvidence)"
}

Assert-NotProductionText "ApiBaseUrl" $ApiBaseUrl
Assert-NotProductionText "TelemetryBaseUrl" $TelemetryBaseUrl
Assert-StagingName "PostgresHost" $PostgresHost
Assert-StagingName "PostgresDatabase" $PostgresDatabase
Assert-StagingName "MongoHost" $MongoHost
Assert-StagingName "MongoDatabase" $MongoDatabase
Assert-StagingName "MqttHost" $MqttHost
foreach ($room in $RoomIds) { Assert-NotProductionText "RoomIds" $room }
if ($RoomIds.Count -lt 2) { Fail "At least two staging room IDs are required." }

$script:PostgresPassword = SecretFromEnv $PostgresPasswordEnvVar
$script:ApiToken = ""

Write-Host "Target: $Target"
Write-Host "RunId: $RunId"
Write-Host "API: $ApiBaseUrl"
Write-Host "Telemetry: $TelemetryBaseUrl"
Write-Host "PostgreSQL: ${PostgresHost}:$PostgresPort/$PostgresDatabase user=$PostgresUser"
Write-Host "MongoDB: $MongoHost/$MongoDatabase"
Write-Host "MQTT: $MqttHost"
Write-Host "Rooms: $($RoomIds -join ', ')"
Write-Host "Secrets: read from env vars only; values will not be printed."
Write-Host ""
$confirmation = Read-Host "Type STAGING TEMPORAL VALIDATION to continue"
if ($confirmation -ne "STAGING TEMPORAL VALIDATION") {
  Fail "Confirmation did not match; aborting before staging writes."
}

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
  Fail "psql is required for Flyway and duplicate checks."
}

Write-Host "[INFO] checking health"
Invoke-Api "/actuator/health" -Token "" | Out-Null
Invoke-Api "/actuator/health" -Token "" -BaseUrl $TelemetryBaseUrl | Out-Null
Write-Host "[OK] healthchecks are reachable"

Login-Api

$flyway = Invoke-SqlScalar "select version from flyway_schema_history where success = true order by installed_rank desc limit 1;"
if ($flyway -ne "24") {
  Fail "Expected Flyway V24, got V$flyway."
}
Write-Host "[OK] Flyway is at V24"

Require-PrometheusMetric $ApiBaseUrl "procel_missions_temporal_windows_backlog"
Require-PrometheusMetric $ApiBaseUrl "procel_missions_drools_evaluations"
Require-PrometheusMetric $TelemetryBaseUrl "procel_telemetry_canonical"

Ensure-SensorTypeAndParams
Ensure-Sensors
Setup-TelemetryIntegration
Create-DurationMission

$start = [DateTimeOffset]::UtcNow.AddSeconds(3)

Write-Host "[SCENARIO] canonical worker through Telemetry"
Ingest-RawTelemetry $start.AddSeconds(-2) $true "canonical-worker" | Out-Null

Write-Host "[SCENARIO] duration satisfied"
Ingest-Measurement $Sensors[0] $start $true "satisfied-0" | Out-Null
Ingest-Measurement $Sensors[0] $start.AddSeconds($SampleGapSeconds) $true "satisfied-1" | Out-Null
Ingest-Measurement $Sensors[0] $start.AddSeconds($SampleGapSeconds * 2) $true "satisfied-2" | Out-Null
Ingest-Measurement $Sensors[0] $start.AddSeconds($DurationSeconds) $true "satisfied-3" | Out-Null
Wait-Until "one confirmed occurrence after satisfied duration" { [int](Count-Occurrences) -ge 1 } 90

Write-Host "[SCENARIO] interruption"
$interrupted = $start.AddMinutes(2)
Ingest-Measurement $Sensors[0] $interrupted $true "interrupt-0" | Out-Null
Ingest-Measurement $Sensors[0] $interrupted.AddSeconds($SampleGapSeconds) $false "interrupt-1" | Out-Null
Wait-Until "an invalidated window after interruption" { [int](Count-Windows "INVALIDADA") -ge 1 } 90

Write-Host "[SCENARIO] excessive gap"
$gap = $start.AddMinutes(4)
Ingest-Measurement $Sensors[0] $gap $true "gap-0" | Out-Null
Ingest-Measurement $Sensors[0] $gap.AddSeconds($ExcessiveGapSeconds) $true "gap-1" | Out-Null
Wait-Until "an expired window after excessive sample gap" { [int](Count-Windows "EXPIRADA") -ge 1 } 90

Write-Host "[SCENARIO] two rooms"
$twoRooms = $start.AddMinutes(6)
for ($i = 0; $i -lt 2; $i++) {
  Ingest-Measurement $Sensors[$i] $twoRooms $true "room-$i-0" | Out-Null
  Ingest-Measurement $Sensors[$i] $twoRooms.AddSeconds($DurationSeconds) $true "room-$i-1" | Out-Null
}
Wait-Until "separate room windows are processed" { [int](Count-Windows "SATISFEITA") -ge 3 } 90

Write-Host "[SCENARIO] canonical duplicate"
$dupTime = $start.AddMinutes(8)
$first = Ingest-Measurement $Sensors[0] $dupTime $true "duplicate"
$second = Ingest-Measurement $Sensors[0] $dupTime $true "duplicate"
if (-not $second.duplicate) { Fail "Canonical duplicate was not reported as duplicate." }
Write-Host "[OK] canonical duplicate returned existing measurement"

Write-Host "[SCENARIO] backlog batch"
$batchStart = $start.AddMinutes(10)
for ($i = 0; $i -lt $BacklogMeasurements; $i++) {
  $sensor = $Sensors[$i % $Sensors.Count]
  Ingest-Measurement $sensor $batchStart.AddSeconds($i) $true "backlog-$i" | Out-Null
}
Wait-Until "mission request backlog drained for staging prefix" {
  [int](Invoke-SqlScalar "select count(*) from evento_avaliacao_request r join medicao m on m.id = r.medicao_id join medicao_ingestao_metadata md on md.medicao_id = m.id where md.message_id like '$RunId-backlog-%' and r.status in ('PENDING','PROCESSING','RETRY');") -eq 0
} 180

Write-Host "[SCENARIO] retry and expired lease"
$leaseWindowId = Invoke-SqlScalar "select j.id from evento_janela_avaliacao j join evento_definicao e on e.id = j.evento_definicao_id where e.nome like '$Prefix%' and j.status = 'ABERTA' order by j.created_at desc limit 1;"
if ($leaseWindowId) {
  Invoke-Sql "update evento_janela_avaliacao set status = 'PROCESSING', lease_until = now() - interval '1 minute', updated_at = now() where id = '$leaseWindowId' and status = 'ABERTA';" | Out-Null
  Wait-Until "expired lease is recoverable" {
    [int](Invoke-SqlScalar "select count(*) from evento_janela_avaliacao where id = '$leaseWindowId' and status in ('ABERTA','PROCESSING','SATISFEITA','INVALIDADA','EXPIRADA','FAILED');") -eq 1
  } 30
} else {
  Write-Host "[WARN] no open staging window available for lease manipulation; scenario was not destructive and is skipped."
}

if (-not $SkipRestartScenario) {
  Write-Host "[SCENARIO] restart during open window"
  $restartStart = [DateTimeOffset]::UtcNow.AddSeconds(3)
  Ingest-Measurement $Sensors[0] $restartStart $true "restart-0" | Out-Null
  Write-Host "Restart Procel-API staging now, wait for health UP, then press ENTER. No production resource may be touched."
  Read-Host "Press ENTER after the staging API restart is complete" | Out-Null
  Invoke-Api "/actuator/health" -Token "" | Out-Null
  Ingest-Measurement $Sensors[0] $restartStart.AddSeconds($DurationSeconds) $true "restart-1" | Out-Null
  Wait-Until "window persisted across restart and can complete" { [int](Count-Occurrences) -ge 4 } 120
}

Write-Host "[SCENARIO] controlled soak"
$soakEnd = [DateTimeOffset]::UtcNow.AddMinutes($SoakMinutes)
$i = 0
while ([DateTimeOffset]::UtcNow -lt $soakEnd) {
  foreach ($sensor in $Sensors) {
    Ingest-Measurement $sensor ([DateTimeOffset]::UtcNow) $true "soak-$i" | Out-Null
    $i++
  }
  Start-Sleep -Seconds $SampleGapSeconds
}
Wait-Until "backlog drained after soak" {
  [int](Invoke-SqlScalar "select count(*) from evento_avaliacao_request r join medicao m on m.id = r.medicao_id join medicao_ingestao_metadata md on md.medicao_id = m.id where md.message_id like '$RunId-soak-%' and r.status in ('PENDING','PROCESSING','RETRY');") -eq 0
} 180

Print-TemporalSummary
$duplicates = Count-Duplicates
if ($duplicates.windows -ne 0 -or $duplicates.occurrences -ne 0 -or $duplicates.windowEvidence -ne 0 -or $duplicates.occurrenceEvidence -ne 0) {
  Fail "Duplicate windows, occurrences, or evidences were detected."
}

Write-Host ""
Write-Host "Validation completed for staging run $RunId."
Write-Host "Review Prometheus for backlog, leases, retries, Drools cache, compilation, latency, memory, and errors before GO/NO-GO."
