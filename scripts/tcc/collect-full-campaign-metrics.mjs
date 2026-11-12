import { createHash } from "node:crypto";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "../..");
const runTag = process.env.TCC_RUN_TAG ?? "20261112-04";
const inventoryTag = process.env.TCC_INVENTORY_TAG ?? "20261112-02";
const artifactRoot = resolve(
  process.env.TCC_ARTIFACT_ROOT ?? join(repoRoot, "docs/tcc/full-campaign-" + runTag),
);
const harnessRoot = resolve(
  process.env.TCC_HARNESS_PATH ?? "C:/GitHub/PROCEL-demo-harness",
);
const apiBase = (process.env.PROBEN_API_BASE_URL ?? "https://procel.servehttp.com").replace(/\/+$/, "");
const email = process.env.PROBEN_ADMIN_EMAIL;
const password = process.env.PROBEN_ADMIN_PASSWORD;
if (!email || !password) {
  throw new Error("Set PROBEN_ADMIN_EMAIL and PROBEN_ADMIN_PASSWORD in the process environment.");
}

const loginResponse = await fetch(`${apiBase}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email, password }),
});
const login = await readJson(loginResponse);
if (!loginResponse.ok) throw new Error(`Login failed: HTTP ${loginResponse.status}`);
const token = login.accessToken;

async function readJson(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return text;
  }
}

async function api(path) {
  const response = await fetch(`${apiBase}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(`GET ${path}: HTTP ${response.status} ${JSON.stringify(body)}`);
  }
  return body;
}

const qualifications = JSON.parse(
  await readFile(join(harnessRoot, `runs/tcc-${inventoryTag}-sensor-qualifications.json`), "utf8"),
);
const rooms = JSON.parse(
  await readFile(join(harnessRoot, `rooms-${inventoryTag}.json`), "utf8"),
);
const ruleIds = new Set(qualifications.createdRules.map((rule) => rule.id));
const runDirs = (await readdir(join(artifactRoot, "runs"), { withFileTypes: true }))
  .filter((entry) => entry.isDirectory())
  .map((entry) => join(artifactRoot, "runs", entry.name))
  .sort();
const enrichedRuns = [];
const allHashes = [];

function quantile(values, probability) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted.length ? sorted[Math.max(0, Math.ceil(probability * sorted.length) - 1)] : null;
}

function describe(values) {
  const numbers = values.filter(Number.isFinite);
  if (!numbers.length) return { n: 0, mean: null, sampleStandardDeviation: null, ci95: null };
  const mean = numbers.reduce((sum, value) => sum + value, 0) / numbers.length;
  const sampleStandardDeviation = numbers.length < 2
    ? 0
    : Math.sqrt(numbers.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (numbers.length - 1));
  const tCriticalDf2 = 4.3026527299;
  const margin = numbers.length === 3 ? tCriticalDf2 * sampleStandardDeviation / Math.sqrt(3) : null;
  return {
    n: numbers.length,
    mean,
    sampleStandardDeviation,
    ci95: margin === null ? null : [mean - margin, mean + margin],
  };
}

for (const runDir of runDirs) {
  const summary = JSON.parse(await readFile(join(runDir, "summary.json"), "utf8"));
  const missionMetricsPath = join(runDir, "mission-metrics.json");
  const missionMetrics = JSON.parse(await readFile(missionMetricsPath, "utf8"));
  const from = new Date(Date.parse(summary.startedAt) - 1000).toISOString();
  const to = new Date(Date.parse(summary.finishedAt) + 1000).toISOString();
  const measurements = (await Promise.all(rooms.map(async ({ sensorId }) => {
    const query = new URLSearchParams({ from, to, page: "0", limit: "1000" });
    return api(`/api/sensors/${encodeURIComponent(sensorId)}/medicoes?${query}`);
  }))).flat().filter((measurement) => {
    const timestamp = Date.parse(measurement.timestamp);
    return timestamp >= Date.parse(summary.startedAt) - 1000 && timestamp <= Date.parse(summary.finishedAt) + 1000;
  });

  const qualificationCounts = {};
  const evaluationsByParameter = {};
  const deliveryDelaysMs = [];
  let evaluationCount = 0;
  let numericParameterValueCount = 0;
  for (const measurement of measurements) {
    deliveryDelaysMs.push(Date.parse(measurement.receivedAt) - Date.parse(measurement.timestamp));
    numericParameterValueCount += Object.keys(measurement.valores ?? {}).length;
    for (const [parameter, evaluations] of Object.entries(measurement.qualificacoes ?? {})) {
      for (const evaluation of evaluations) {
        if (!ruleIds.has(evaluation.regraParametroId)) continue;
        evaluationCount++;
        qualificationCounts[evaluation.resultado] = (qualificationCounts[evaluation.resultado] ?? 0) + 1;
        evaluationsByParameter[parameter] ??= {};
        evaluationsByParameter[parameter][evaluation.resultado] =
          (evaluationsByParameter[parameter][evaluation.resultado] ?? 0) + 1;
      }
    }
  }

  const missionId = missionMetrics.eventDefinition.missaoId;
  const pointLedgers = await Promise.all(missionMetrics.studentPoints.map(async ({ pessoaId }) => {
    const query = new URLSearchParams({ page: "0", size: "100" });
    const page = await api(`/api/pessoas/${encodeURIComponent(pessoaId)}/pontos/lancamentos?${query}`);
    return {
      pessoaId,
      totalCurrentPoints: missionMetrics.studentPoints.find((item) => item.pessoaId === pessoaId)?.saldoTotalPontos ?? null,
      missionEntries: page.content.filter((entry) => entry.missaoId === missionId),
    };
  }));
  const missionEntries = pointLedgers.flatMap((person) => person.missionEntries);
  const occurrenceRows = missionMetrics.occurrencePage.content ?? [];
  const windowRows = missionMetrics.windowPage.content ?? [];
  const detectionDelaysMs = occurrenceRows
    .filter((occurrence) => occurrence.detectadoEm && occurrence.inicioEm)
    .map((occurrence) => Date.parse(occurrence.detectadoEm) - Date.parse(occurrence.inicioEm));
  const windowDetectionDelaysMs = windowRows
    .filter((window) => window.detectadoEm && window.inicioEm)
    .map((window) => Date.parse(window.detectadoEm) - Date.parse(window.inicioEm));

  const serverMetrics = {
    collectedAt: new Date().toISOString(),
    source: "Authenticated canonical measurement API; qualification counts are restricted to the run's linked rule IDs.",
    expectedCanonicalMeasurements: summary.count,
    canonicalMeasurementsReturned: measurements.length,
    expectedParameterValues: summary.count * qualifications.parameterCount,
    observedParameterValues: numericParameterValueCount,
    expectedQualifications: summary.count * qualifications.parameterCount,
    observedQualifications: evaluationCount,
    reconciliationComplete:
      measurements.length === summary.count &&
      numericParameterValueCount === summary.count * qualifications.parameterCount &&
      evaluationCount === summary.count * qualifications.parameterCount,
    resultsByStatus: qualificationCounts,
    evaluationsByParameter,
    sourceToCanonicalDelayMs: {
      n: deliveryDelaysMs.length,
      mean: deliveryDelaysMs.length ? deliveryDelaysMs.reduce((sum, value) => sum + value, 0) / deliveryDelaysMs.length : null,
      p50: quantile(deliveryDelaysMs, 0.5),
      p95: quantile(deliveryDelaysMs, 0.95),
      p99: quantile(deliveryDelaysMs, 0.99),
    },
    syntheticAnomaliesExpectedByParameter: summary.sensorRuleQualification.injectedOutOfRangeSamplesByParameter,
    expectedSyntheticAlertEvaluations: summary.sensorRuleQualification.expectedAlertEvaluations,
    pointsLedger: {
      students: pointLedgers.length,
      missionSpecificEntries: missionEntries.length,
      missionSpecificPoints: missionEntries.reduce((sum, entry) => sum + entry.pontos, 0),
      ledgerEntriesByType: Object.fromEntries(
        [...new Set(missionEntries.map((entry) => entry.tipo))].map((type) => [type, missionEntries.filter((entry) => entry.tipo === type).length]),
      ),
    },
  };
  const missionSummary = {
    ...missionMetrics,
    pointsLedgerByStudent: pointLedgers,
    eventDetectionDelayMs: {
      n: detectionDelaysMs.length,
      mean: detectionDelaysMs.length ? detectionDelaysMs.reduce((sum, value) => sum + value, 0) / detectionDelaysMs.length : null,
      p50: quantile(detectionDelaysMs, 0.5),
      p95: quantile(detectionDelaysMs, 0.95),
      p99: quantile(detectionDelaysMs, 0.99),
    },
    windowDetectionDelayMs: {
      n: windowDetectionDelaysMs.length,
      mean: windowDetectionDelaysMs.length ? windowDetectionDelaysMs.reduce((sum, value) => sum + value, 0) / windowDetectionDelaysMs.length : null,
      p50: quantile(windowDetectionDelaysMs, 0.5),
      p95: quantile(windowDetectionDelaysMs, 0.95),
      p99: quantile(windowDetectionDelaysMs, 0.99),
    },
  };
  await writeFile(join(runDir, "server-metrics.json"), `${JSON.stringify(serverMetrics, null, 2)}\n`);
  await writeFile(missionMetricsPath, `${JSON.stringify(missionSummary, null, 2)}\n`);
  enrichedRuns.push({ runDir, summary, serverMetrics, missionSummary });
}

const modeStats = {};
for (const mode of [...new Set(enrichedRuns.map((run) => run.summary.runId.replace(/-r\d+$/, "").split(`${runTag}-`)[1]))]) {
  const runs = enrichedRuns.filter((run) => run.summary.runId.startsWith(`${runTag}-${mode}-`));
  modeStats[mode] = {
    repetitions: runs.length,
    publisherAcknowledgements: describe(runs.map((run) => run.summary.brokerAckRate)),
    achievedMessagesPerSecond: describe(runs.map((run) => run.summary.achievedRatePerSecond)),
    publisherAckMeanMs: describe(runs.map((run) => run.summary.clientLatencyMs.mean)),
    publisherAckP95Ms: describe(runs.map((run) => run.summary.clientLatencyMs.p95)),
    publisherAckP99Ms: describe(runs.map((run) => run.summary.clientLatencyMs.p99)),
    sourceToCanonicalMeanMs: describe(runs.map((run) => run.serverMetrics.sourceToCanonicalDelayMs.mean)),
    canonicalMeasurements: describe(runs.map((run) => run.serverMetrics.canonicalMeasurementsReturned)),
    qualificationEvaluations: describe(runs.map((run) => run.serverMetrics.observedQualifications)),
    qualificationAlerts: describe(runs.map((run) => run.serverMetrics.resultsByStatus.ALERTA ?? 0)),
    occurrences: describe(runs.map((run) => run.missionSummary.occurrencePage.totalElements)),
    temporalWindows: describe(runs.map((run) => run.missionSummary.windowPage.totalElements)),
    completedActivities: describe(runs.map((run) => run.missionSummary.activities.filter((activity) => activity.status === "CONCLUIDA").length)),
    awardedMissionPoints: describe(runs.map((run) => run.serverMetrics.pointsLedger.missionSpecificPoints)),
  };
}

const resultsPath = join(artifactRoot, "results.json");
const result = JSON.parse(await readFile(resultsPath, "utf8"));
result.statisticalSummary = {
  method: "Per-mode mean, sample standard deviation and two-sided 95% Student t interval (df=2) across three independent repetitions. Per-run ACK success also retains the harness Wilson interval.",
  modeStats,
};
result.reconciliations = {
  runCount: enrichedRuns.length,
  expectedMessages: enrichedRuns.reduce((sum, run) => sum + run.summary.count, 0),
  canonicalMessages: enrichedRuns.reduce((sum, run) => sum + run.serverMetrics.canonicalMeasurementsReturned, 0),
  expectedParameterValues: enrichedRuns.reduce((sum, run) => sum + run.serverMetrics.expectedParameterValues, 0),
  observedParameterValues: enrichedRuns.reduce((sum, run) => sum + run.serverMetrics.observedParameterValues, 0),
  expectedQualifications: enrichedRuns.reduce((sum, run) => sum + run.serverMetrics.expectedQualifications, 0),
  observedQualifications: enrichedRuns.reduce((sum, run) => sum + run.serverMetrics.observedQualifications, 0),
  acknowledgements: enrichedRuns.reduce((sum, run) => sum + run.summary.brokerAcknowledged, 0),
  failures: enrichedRuns.reduce((sum, run) => sum + run.summary.failed, 0),
  pointsLedgerEntries: enrichedRuns.reduce((sum, run) => sum + run.serverMetrics.pointsLedger.missionSpecificEntries, 0),
  allMeasurementsAndQualificationsReconciled: enrichedRuns.every((run) => run.serverMetrics.reconciliationComplete),
};
result.runs = enrichedRuns.map((run) => ({
  runId: run.summary.runId,
  mode: run.summary.runId.replace(`${runTag}-`, "").replace(/-r\d+$/, ""),
  attempted: run.summary.attempted,
  brokerAcknowledged: run.summary.brokerAcknowledged,
  failed: run.summary.failed,
  brokerAckRate: run.summary.brokerAckRate,
  achievedRatePerSecond: run.summary.achievedRatePerSecond,
  clientLatencyMs: run.summary.clientLatencyMs,
  serverMetrics: run.serverMetrics,
  occurrenceCount: run.missionSummary.occurrencePage.totalElements,
  occurrenceStatuses: Object.fromEntries(
    [...new Set((run.missionSummary.occurrencePage.content ?? []).map((item) => item.status))]
      .map((status) => [status, run.missionSummary.occurrencePage.content.filter((item) => item.status === status).length]),
  ),
  windowCount: run.missionSummary.windowPage.totalElements,
  windowStatuses: Object.fromEntries(
    [...new Set((run.missionSummary.windowPage.content ?? []).map((item) => item.status))]
      .map((status) => [status, run.missionSummary.windowPage.content.filter((item) => item.status === status).length]),
  ),
  assignedStudents: run.missionSummary.assignedStudentCount,
  completedActivities: run.missionSummary.activities.filter((activity) => activity.status === "CONCLUIDA").length,
  missionSpecificPoints: run.serverMetrics.pointsLedger.missionSpecificPoints,
  eventDetectionDelayMs: run.missionSummary.eventDetectionDelayMs,
  windowDetectionDelayMs: run.missionSummary.windowDetectionDelayMs,
}));

const jobId = result.aggregationJob?.id;
if (jobId && result.aggregationJob.status === "COMPLETED") {
  const first = enrichedRuns[0].summary.startedAt;
  const last = enrichedRuns.reduce((latest, run) =>
    Date.parse(run.summary.finishedAt) > Date.parse(latest) ? run.summary.finishedAt : latest, first);
  const query = new URLSearchParams({
    from: first,
    to: new Date(Date.parse(last) + 1000).toISOString(),
    sensorTypeName: "TCC_DEMO_SENSOR_20261112-02",
    page: "0",
    size: "100",
  });
  const buckets = await api(`/api/analytics/numeric-buckets?${query}`);
  result.bucketQuery = {
    filters: Object.fromEntries(query.entries()),
    page: buckets,
    retrievedAt: new Date().toISOString(),
  };
}

await writeFile(resultsPath, `${JSON.stringify(result, null, 2)}\n`);

const md = [];
md.push(`# Campanha TCC ${runTag}`, "");
md.push(`- Ambiente: staging isolado (${result.baseUrl})`, `- Período: ${result.startedAt} a ${result.finishedAt}`);
md.push(`- 10 salas/sensores virtuais; 20 estudantes; quatro modos, três repetições por modo.`);
md.push(`- Carga alvo: 60 mensagens por repetição a 2 msg/s; total esperado ${result.reconciliations.expectedMessages}.`);
md.push(`- Cada mensagem tem 13 parâmetros; valores de sala sintéticos, sem alegação de calibração física.`);
md.push(`- ACK do publisher mede publicação/ack do broker; atraso canônico é medido separadamente por timestamp de origem e recebimento.`);
md.push(`- Temperaturas externas fixas de contexto WeatherAPI: média 7 dias 18,23 °C; observação 25,6 °C.`);
md.push("", "## Resumo estatístico por modo", "");
md.push("| Modo | n | ACK | Mensagens/s (média ± DP) | ACK médio ms (média ± DP) | ACK p95 ms (média ± DP) | Cânonicas/run | Avaliações/run | Alertas/run | Ocorrências/run | Janelas/run | Atividades concluídas/run | Pontos/run |", "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
for (const [mode, stats] of Object.entries(modeStats)) {
  const fmt = (value) => value.mean === null ? "—" : `${value.mean.toFixed(2)} ± ${value.sampleStandardDeviation.toFixed(2)}`;
  md.push(`| ${mode} | ${stats.repetitions} | ${stats.publisherAcknowledgements.mean === null ? "—" : `${(stats.publisherAcknowledgements.mean * 100).toFixed(1)}%`} | ${fmt(stats.achievedMessagesPerSecond)} | ${fmt(stats.publisherAckMeanMs)} | ${fmt(stats.publisherAckP95Ms)} | ${fmt(stats.canonicalMeasurements)} | ${fmt(stats.qualificationEvaluations)} | ${fmt(stats.qualificationAlerts)} | ${fmt(stats.occurrences)} | ${fmt(stats.temporalWindows)} | ${fmt(stats.completedActivities)} | ${fmt(stats.awardedMissionPoints)} |`);
}
md.push("", "## Reconciliação", "", `- Publicações aceitas: ${result.reconciliations.acknowledgements}; falhas: ${result.reconciliations.failures}.`, `- Medições canônicas: ${result.reconciliations.canonicalMessages}/${result.reconciliations.expectedMessages}.`, `- Valores de parâmetros: ${result.reconciliations.observedParameterValues}/${result.reconciliations.expectedParameterValues}.`, `- Avaliações do grupo de regras: ${result.reconciliations.observedQualifications}/${result.reconciliations.expectedQualifications}.`, `- Lançamentos de pontos associados às missões: ${result.reconciliations.pointsLedgerEntries}.`, `- Reconciliação integral de medições/avaliações: ${result.reconciliations.allMeasurementsAndQualificationsReconciled ? "sim" : "não; consultar os métricos por repetição"}.`);
md.push("", "## Escopo e limites", "", "A campanha é um ensaio controlado de fluxo completo em taxa sustentada de 2 msg/s e não mede o ponto de saturação ou capacidade máxima. O publisher confirma o broker; persistência, qualificações e efeitos de missão são conferidos por consultas autenticadas à API. Dados ambientais internos, ocupação, energia, ruído e CO₂ são valores sintéticos. Os alertas representam casos fora da faixa de especificação cadastrada, não medições físicas. Os intervalos t com n=3 são exploratórios e devem ser apresentados junto dos valores individuais e do desvio padrão.");
md.push("", "## Artefatos", "", "Cada pasta `runs/<runId>` contém o fluxo de eventos NDJSON, manifesto/hash, resumo do publisher, reconciliação canônica e métricas de missões, janelas, atividades e ledger de pontos. `preexisting-active-events.json` guarda os quatro eventos temporariamente desativados e restaurados após os testes.");
await writeFile(join(artifactRoot, "results.md"), `${md.join("\n")}\n`);

for (const runDir of runDirs) {
  for (const name of ["events.ndjson", "manifest.json", "summary.json", "server-metrics.json", "mission-metrics.json"]) {
    const path = join(runDir, name);
    const digest = createHash("sha256").update(await readFile(path)).digest("hex");
    allHashes.push(`${digest}  ${path.replace(artifactRoot + "\\", "")}`);
  }
}
await writeFile(join(artifactRoot, "SHA256SUMS.txt"), `${allHashes.join("\n")}\n`);
console.log(JSON.stringify(result.reconciliations, null, 2));
