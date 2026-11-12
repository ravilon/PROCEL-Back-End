param(
    [string]$BaseUrl = "https://procel.servehttp.com",
    [string]$HarnessPath = "C:\GitHub\PROCEL-demo-harness",
    [string]$RunTag = "20261112-04",
    [string]$InventoryTag = "20261112-02"
)

$ErrorActionPreference = "Stop"
if (-not $env:PROBEN_ADMIN_EMAIL -or -not $env:PROBEN_ADMIN_PASSWORD) {
    throw "Set PROBEN_ADMIN_EMAIL and PROBEN_ADMIN_PASSWORD in the process environment."
}
if (-not (Test-NetConnection 127.0.0.1 -Port 13001 -InformationLevel Quiet)) {
    throw "The publisher SSH tunnel at localhost:13001 is not available."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$artifactRoot = Join-Path $repoRoot "docs\tcc\full-campaign-$RunTag"
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
function Save-Json($Path, $Value) {
    [System.IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 30), $utf8)
}
function Api($Method, $Path, $Body = $null) {
    $params = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $script:headers; TimeoutSec = 90 }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @params
}

$login = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{ email = $env:PROBEN_ADMIN_EMAIL; password = $env:PROBEN_ADMIN_PASSWORD } | ConvertTo-Json)
$script:headers = @{ Authorization = "Bearer $($login.accessToken)" }
$workerStatus = Api GET "/api/admin/missions/workers/status"
if (-not $workerStatus.evaluation.enabled -or -not $workerStatus.temporalWindows.enabled) {
    throw "Evaluation and temporal-window workers must both be enabled."
}

$rooms = Get-Content (Join-Path $HarnessPath "rooms-$InventoryTag.json") -Raw | ConvertFrom-Json
$qualification = Get-Content (Join-Path $HarnessPath "runs\tcc-$InventoryTag-sensor-qualifications.json") -Raw | ConvertFrom-Json
$students = 1..20 | ForEach-Object { "tcc-demo-20261112-02-{0:D2}" -f $_ }
$activeEventsPage = Api GET "/api/admin/missions/events?active=true&page=0&size=100"
$preexisting = @()
foreach ($summary in $activeEventsPage.content | Where-Object { $_.tipoDisparo -eq "MEDICAO_RECEBIDA" }) {
    $preexisting += Api GET "/api/mission-events/$($summary.id)"
}
Save-Json (Join-Path $artifactRoot "preexisting-active-events.json") $preexisting
$modeSpecs = @(
    @{ Name = "instantaneo"; Trigger = "MEDICAO_RECEBIDA"; Mode = "INSTANTANEO"; Param = "ebc0f250-82e7-4546-898c-17eaa65478fe"; Operator = "GTE"; Num = 25; Bool = $null; Window = $null; Duration = $null; Gap = $null },
    @{ Name = "transicao"; Trigger = "MEDICAO_RECEBIDA"; Mode = "TRANSICAO"; Param = "b60e9ba8-ec79-4b6b-9ce9-aedde00c736b"; Operator = "EQ"; Num = $null; Bool = $true; Window = $null; Duration = $null; Gap = $null },
    @{ Name = "duracao"; Trigger = "MEDICAO_RECEBIDA"; Mode = "DURACAO"; Param = "b60e9ba8-ec79-4b6b-9ce9-aedde00c736b"; Operator = "EQ"; Num = $null; Bool = $true; Window = 30; Duration = 10; Gap = 10 },
    @{ Name = "janela-encerrada"; Trigger = "JANELA_ENCERRADA"; Mode = "INSTANTANEO"; Param = "b60e9ba8-ec79-4b6b-9ce9-aedde00c736b"; Operator = "EQ"; Num = $null; Bool = $true; Window = 10; Duration = $null; Gap = $null }
)
$runRecords = [System.Collections.Generic.List[object]]::new()
$startedAt = [DateTimeOffset]::UtcNow
$eventDefinitions = [System.Collections.Generic.List[object]]::new()

try {
    foreach ($event in $preexisting) {
        Api PUT "/api/mission-events/$($event.id)" @{
            nome = $event.nome; descricao = $event.descricao; tipoDisparo = $event.tipoDisparo
            modoAvaliacao = $event.modoAvaliacao; operadorLogico = $event.operadorLogico
            politicaAtribuicao = $event.politicaAtribuicao; janelaSegundos = $event.janelaSegundos
            duracaoMinimaSegundos = $event.duracaoMinimaSegundos; quantidadeNecessaria = $event.quantidadeNecessaria
            cooldownSegundos = $event.cooldownSegundos; ordem = $event.ordem; ativo = $false
            papel = $event.papel; lacunaMaximaSegundos = $event.lacunaMaximaSegundos
        } | Out-Null
    }

    foreach ($spec in $modeSpecs) {
        for ($rep = 1; $rep -le 3; $rep++) {
            $runId = "$RunTag-$($spec.Name)-r$rep"
            $mission = Api POST "/api/missoes" @{
                titulo = "TCC $RunTag $($spec.Name) repeticao $rep"; descricao = "Execucao controlada de $($spec.Mode) com sensores virtuais."
                tipo = "Individual"; pontos = 5; ativo = $true; parentId = $null; cicloTipo = "UNICA"
                progressoNecessario = 1; conclusaoAutomatica = $true
            }
            $eventBody = @{
                nome = "TCC $runId $($spec.Mode)"; descricao = "Teste controlado de gatilho $($spec.Trigger) e avaliacao $($spec.Mode)."
                tipoDisparo = $spec.Trigger; modoAvaliacao = $spec.Mode; operadorLogico = "ALL"
                politicaAtribuicao = "ALUNOS_VINCULADOS"; janelaSegundos = $spec.Window
                duracaoMinimaSegundos = $spec.Duration; quantidadeNecessaria = 1; cooldownSegundos = 0
                ordem = 1; ativo = $false; papel = "PROGRESSO"; lacunaMaximaSegundos = $spec.Gap
            }
            $eventDefinition = Api POST "/api/missions/$($mission.id)/events" $eventBody
            $condition = @{
                parametroDefId = $spec.Param; operador = $spec.Operator; valorNumeric1 = $spec.Num
                valorNumeric2 = $null; valorBoolean = $spec.Bool; valorText = $null; agregacao = "ULTIMO"
                obrigatoria = $true; ordem = 1; fonte = "PARAMETRO_VALOR"; regraParametroId = $null
                resultadoEsperado = $null; ativo = $true
            }
            Api POST "/api/mission-events/$($eventDefinition.id)/conditions" $condition | Out-Null
            foreach ($student in $students) {
                Api POST "/api/pessoas/$student/atividades" @{ missaoId = $mission.id } | Out-Null
            }
            $activeEventBody = $eventBody.Clone(); $activeEventBody.ativo = $true
            $inactiveEventBody = $eventBody.Clone(); $inactiveEventBody.ativo = $false
            $eventDefinition = Api PUT "/api/mission-events/$($eventDefinition.id)" $activeEventBody
            $eventDefinitions.Add([pscustomobject]@{ mode = $spec.Name; repetition = $rep; missionId = $mission.id; eventId = $eventDefinition.id; students = $students })

            $runDir = Join-Path $artifactRoot "runs\$runId"
            New-Item -ItemType Directory -Force -Path $runDir | Out-Null
            $env:PUBLISH_URL = "http://127.0.0.1:13001/publish"
            $env:PUBLISH_API_KEY = "ssh-tunnel-injects-the-staging-key"
            $env:PUBLISH_PRODUCER_ID = "tcc-$runId"
            Push-Location $HarnessPath
            try {
                & node .\simulate-demo.mjs --count 60 --rate 2 --anomaly-rate 0.1 --seed (20261112040 + (($modeSpecs.IndexOf($spec) * 3) + $rep)) `
                    --run-id $runId --external-mean-c 18.23 --external-temperature-c 25.6 --course-id 1 --mission-id $mission.id `
                    --rooms-file ".\rooms-$InventoryTag.json" --qualification-file ".\runs\tcc-$InventoryTag-sensor-qualifications.json" --output $runDir
                if ($LASTEXITCODE -ne 0) { throw "Publisher simulation failed for $runId (exit $LASTEXITCODE)." }
            } finally { Pop-Location }

            $summary = Get-Content (Join-Path $runDir "summary.json") -Raw | ConvertFrom-Json
            $from = [DateTimeOffset]::Parse($summary.startedAt).ToString("o")
            $to = [DateTimeOffset]::Parse($summary.finishedAt).AddMinutes(5).ToString("o")
            $server = $null
            for ($attempt = 1; $attempt -le 12; $attempt++) {
                $measurements = @()
                foreach ($room in $rooms) {
                    $query = [Uri]::EscapeDataString($from)
                    $queryTo = [Uri]::EscapeDataString($to)
                    $measurements += Api GET "/api/sensors/$([Uri]::EscapeDataString($room.sensorId))/medicoes?from=$query&to=$queryTo&page=0&limit=1000"
                }
                if ($measurements.Count -ge 60) { break }
                Start-Sleep -Seconds 5
            }
            $ruleIds = @{}; foreach ($rule in $qualification.createdRules) { $ruleIds[$rule.id] = $true }
            $byResult = @{}
            foreach ($measurement in $measurements) {
                foreach ($parameterEvaluation in $measurement.qualificacoes.PSObject.Properties) {
                    foreach ($evaluation in @($parameterEvaluation.Value)) {
                        if ($ruleIds.ContainsKey($evaluation.regraParametroId)) {
                            $byResult[$evaluation.resultado] = 1 + [int]$byResult[$evaluation.resultado]
                        }
                    }
                }
            }
            $server = [pscustomobject]@{
                canonicalMeasurements = $measurements.Count; expectedMeasurements = 60
                qualificationEvaluations = ($byResult.Values | Measure-Object -Sum).Sum
                expectedQualifications = 780; qualificationResults = $byResult; collectionAttempts = $attempt
            }

            if ($spec.Mode -in @("DURACAO") -or $spec.Trigger -eq "JANELA_ENCERRADA") { Start-Sleep -Seconds 40 }
            $windows = Api GET "/api/admin/missions/windows?eventoDefinicaoId=$($eventDefinition.id)&page=0&size=100"
            $occurrences = Api GET "/api/admin/missions/occurrences?eventoDefinicaoId=$($eventDefinition.id)&page=0&size=100"
            $activities = @(); $balances = @()
            foreach ($student in $students) {
                $activities += (Api GET "/api/pessoas/$student/atividades") | Where-Object { $_.missaoId -eq $mission.id }
                $balances += Api GET "/api/pessoas/$student/pontos"
            }
            Save-Json (Join-Path $runDir "server-metrics.json") $server
            Save-Json (Join-Path $runDir "mission-metrics.json") ([pscustomobject]@{
                eventDefinition = $eventDefinition; windowPage = $windows; occurrencePage = $occurrences
                activities = $activities; studentPoints = $balances; assignedStudentCount = $students.Count
            })
            $runRecords.Add([pscustomobject]@{
                runId = $runId; mode = $spec.Name; repetition = $rep; missionId = $mission.id; eventId = $eventDefinition.id
                summary = $summary; server = $server; activityCount = $activities.Count
                completedActivities = @($activities | Where-Object status -eq "CONCLUIDA").Count
                occurrenceCount = $occurrences.totalElements; windowCount = $windows.totalElements
            })
            Api PUT "/api/mission-events/$($eventDefinition.id)" $inactiveEventBody | Out-Null
        }
    }
} finally {
    foreach ($event in $preexisting) {
        $restore = @{
            nome = $event.nome; descricao = $event.descricao; tipoDisparo = $event.tipoDisparo
            modoAvaliacao = $event.modoAvaliacao; operadorLogico = $event.operadorLogico
            politicaAtribuicao = $event.politicaAtribuicao; janelaSegundos = $event.janelaSegundos
            duracaoMinimaSegundos = $event.duracaoMinimaSegundos; quantidadeNecessaria = $event.quantidadeNecessaria
            cooldownSegundos = $event.cooldownSegundos; ordem = $event.ordem; ativo = $event.ativo
            papel = $event.papel; lacunaMaximaSegundos = $event.lacunaMaximaSegundos
        }
        Api PUT "/api/mission-events/$($event.id)" $restore | Out-Null
    }
    foreach ($created in $eventDefinitions) {
        $current = Api GET "/api/mission-events/$($created.eventId)"
        if ($current.ativo) {
            Api PUT "/api/mission-events/$($created.eventId)" @{
                nome = $current.nome; descricao = $current.descricao; tipoDisparo = $current.tipoDisparo
                modoAvaliacao = $current.modoAvaliacao; operadorLogico = $current.operadorLogico
                politicaAtribuicao = $current.politicaAtribuicao; janelaSegundos = $current.janelaSegundos
                duracaoMinimaSegundos = $current.duracaoMinimaSegundos; quantidadeNecessaria = $current.quantidadeNecessaria
                cooldownSegundos = $current.cooldownSegundos; ordem = $current.ordem; ativo = $false
                papel = $current.papel; lacunaMaximaSegundos = $current.lacunaMaximaSegundos
            } | Out-Null
        }
    }
}

$endedAt = [DateTimeOffset]::UtcNow
$aggregate = Api POST "/api/analytics/aggregation-jobs" @{
    from = $startedAt.ToString("o"); to = $endedAt.AddMinutes(5).ToString("o"); windowDuration = "PT1M"
}
for ($i = 0; $i -lt 180 -and $aggregate.status -notin @("COMPLETED", "FAILED"); $i++) {
    Start-Sleep -Seconds 2
    $aggregate = Api GET "/api/analytics/aggregation-jobs/$($aggregate.id)"
}
$summaryReport = [pscustomobject]@{
    experimentId = "TCC-PROBEN-FULL-$RunTag"; baseUrl = $BaseUrl; startedAt = $startedAt.ToString("o"); finishedAt = $endedAt.ToString("o")
    workerStatus = $workerStatus; selectedRuleEngine = $workerStatus.drools.selectedRuleEngine
    temporalDroolsEnabled = $workerStatus.drools.temporalDroolsEnabled; temporalActivitiesEnabled = $workerStatus.drools.temporalActivitiesEnabled
    roomCount = $rooms.Count; sensorCount = $rooms.Count; studentCount = 20; pointsAwardPerMission = 5
    modes = $modeSpecs.Name; repetitionsPerMode = 3; runs = $runRecords; missionsAndEvents = $eventDefinitions
    aggregationJob = $aggregate; restoredPreexistingEventIds = @($preexisting | ForEach-Object id)
    externalWeatherContextC = @{ sevenDayMean = 18.23; currentObservation = 25.6; provenance = "WeatherAPI observations previously fetched and stored in staging; reused as fixed context for deterministic runs." }
    measurementDataClassification = "All room sensor readings are synthetic; publisher HTTP acknowledgment is not proof of canonical ingestion or downstream processing."
}
Save-Json (Join-Path $artifactRoot "results.json") $summaryReport
Write-Output (Join-Path $artifactRoot "results.json")
