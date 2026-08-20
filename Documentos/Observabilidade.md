# Observabilidade

## Endpoints

Procel-API e Procel-Telemetry publicam:

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
- propaga chamadas do Procel-Telemetry para o Procel-API;
- limpa o MDC ao finalizar a requisição;
- nunca usa JWT, payload, usuário ou dados pessoais como correlation ID.

## Métricas

Procel-Telemetry:

- `procel.telemetry.events`
- `procel.telemetry.events.duration`
- `procel.telemetry.mqtt.messages`
- `procel.telemetry.mqtt.duration`
- `procel.telemetry.mqtt.reconnections`
- `procel.telemetry.canonical.results`
- `procel.telemetry.canonical.duration`
- `procel.telemetry.canonical.retries`
- `procel.telemetry.canonical.failures`
- `procel.telemetry.backlog`

Procel-API:

- `procel.analytics.aggregation.jobs`
- `procel.analytics.aggregation.windows.processed`
- `procel.analytics.aggregation.windows.completed`
- `procel.analytics.aggregation.windows.failed`
- `procel.analytics.aggregation.windows.retries`
- `procel.analytics.aggregation.windows.duration`
- `procel.analytics.buckets.persisted`
- `procel.analytics.queries`
- `procel.analytics.query.errors`
- `procel.analytics.query.duration`

Tags usadas:

- `source`
- `outcome`
- `status`
- `type`

Tags proibidas por alta cardinalidade:

- `messageId`
- `sensorId`
- `rawEventId`
- `jobId`
- `windowId`
- `userId`
- `payload`
- texto livre de erro

## Consultas básicas

Eventos recebidos por origem:

```promql
sum by (source, outcome) (rate(procel_telemetry_events_total[5m]))
```

Mensagens MQTT rejeitadas:

```promql
sum(rate(procel_telemetry_mqtt_messages_total{outcome="rejected"}[5m]))
```

Backlog de telemetria por status:

```promql
procel_telemetry_backlog
```

Falhas canônicas:

```promql
sum by (source) (rate(procel_telemetry_canonical_failures_total[5m]))
```

Janelas de agregação com retry:

```promql
sum(rate(procel_analytics_aggregation_windows_retries_total[5m]))
```

Erros em consultas analíticas:

```promql
sum by (type) (rate(procel_analytics_query_errors_total[5m]))
```

Média de duração de janelas:

```promql
rate(procel_analytics_aggregation_windows_duration_seconds_sum[5m])
/
rate(procel_analytics_aggregation_windows_duration_seconds_count[5m])
```
