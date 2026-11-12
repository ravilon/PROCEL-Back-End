# Observabilidade

## Endpoints

Proben-API e Proben-Telemetry publicam:

- `/actuator/health`
- `/actuator/prometheus`

`health` permanece público para healthchecks. `prometheus` exige autenticação com papel
`ADMIN` porque as aplicações continuam publicadas pelas portas atuais do Compose.

Não são expostos publicamente endpoints como `env`, `configprops`, `beans` ou `heapdump`.

## Correlation ID

O header operacional é `X-Correlation-ID`.

Comportamento:

- aceita valores recebidos quando possuem até 64 caracteres e apenas letras, números, `.`, `_`, `:` ou `-`;
- gera UUID quando o header está ausente ou inválido;
- devolve o valor efetivo no response;
- registra o valor no MDC com chave `correlationId`;
- propaga chamadas do Proben-Telemetry para o Proben-API;
- limpa o MDC ao finalizar a requisição;
- nunca usa JWT, payload, usuário ou dados pessoais como correlation ID.

## Métricas

Proben-Telemetry:

- `proben.telemetry.events`
- `proben.telemetry.events.duration`
- `proben.telemetry.mqtt.messages`
- `proben.telemetry.mqtt.duration`
- `proben.telemetry.mqtt.reconnections`
- `proben.telemetry.canonical.results`
- `proben.telemetry.canonical.duration`
- `proben.telemetry.canonical.retries`
- `proben.telemetry.canonical.failures`
- `proben.telemetry.backlog`

Proben-API:

- `proben.analytics.aggregation.jobs`
- `proben.analytics.aggregation.windows.processed`
- `proben.analytics.aggregation.windows.completed`
- `proben.analytics.aggregation.windows.failed`
- `proben.analytics.aggregation.windows.retries`
- `proben.analytics.aggregation.windows.duration`
- `proben.analytics.buckets.persisted`
- `proben.analytics.queries`
- `proben.analytics.query.errors`
- `proben.analytics.query.duration`
- `proben.missions.activities.created`
- `proben.missions.activities.progressed`
- `proben.missions.activities.completed`
- `proben.missions.beneficiaries.resolved`
- `proben.missions.beneficiaries.empty`
- `proben.missions.activity.processing.failures`
- `proben.missions.xp.granted`
- `proben.missions.xp.amount`
- `proben.missions.xp.duplicates`
- `proben.missions.xp.failures`
- `proben.missions.drools.compilations`
- `proben.missions.drools.compilation.duration`
- `proben.missions.drools.compilation.failures`
- `proben.missions.drools.cache.hits`
- `proben.missions.drools.cache.misses`
- `proben.missions.drools.cache.evictions`
- `proben.missions.drools.evaluations`
- `proben.missions.drools.evaluation.duration`
- `proben.missions.drools.evaluation.failures`
- `proben.missions.drools.facts`
- `proben.missions.drools.limit.rejections`
- `proben.missions.temporal.windows.opened`
- `proben.missions.temporal.windows.satisfied`
- `proben.missions.temporal.windows.expired`
- `proben.missions.temporal.windows.failed`
- `proben.missions.temporal.windows.retries`
- `proben.missions.temporal.windows.backlog`
- `proben.environment.rules.executions`
- `proben.environment.rules.duration`
- `proben.environment.rules.retries`
- `proben.environment.rules.leases.recovered`
- `proben.environment.rules.duplicates.prevented`
- `proben.environment.rules.points`
- `proben.environment.rules.backlog`

As coletas de contexto compartilhado registram execucoes e auditoria persistida
em `contexto_coleta_execucao`. Confira o codigo de observabilidade/health do
modulo para nomes e tags adicionais, pois esta lista manual resume os principais
indicadores e nao substitui `/actuator/prometheus`.

Tags usadas:

- `source`
- `outcome`
- `status`
- `type`
- `mode`
- `result`
- `limit`

Tags proibidas por alta cardinalidade:

- `messageId`
- `sensorId`
- `rawEventId`
- `jobId`
- `windowId`
- `userId`
- `payload`
- texto livre de erro
- ids de evento, ocorrencia, janela, atividade, missao, medicao, sala, sensor ou pessoa

## Consultas básicas

Eventos recebidos por origem:

```promql
sum by (source, outcome) (rate(proben_telemetry_events_total[5m]))
```

Mensagens MQTT rejeitadas:

```promql
sum(rate(proben_telemetry_mqtt_messages_total{outcome="rejected"}[5m]))
```

Backlog de telemetria por status:

```promql
proben_telemetry_backlog
```

Falhas canônicas:

```promql
sum by (source) (rate(proben_telemetry_canonical_failures_total[5m]))
```

Janelas de agregação com retry:

```promql
sum(rate(proben_analytics_aggregation_windows_retries_total[5m]))
```

Erros em consultas analíticas:

```promql
sum by (type) (rate(proben_analytics_query_errors_total[5m]))
```

Média de duração de janelas:

```promql
rate(proben_analytics_aggregation_windows_duration_seconds_sum[5m])
/
rate(proben_analytics_aggregation_windows_duration_seconds_count[5m])
```
