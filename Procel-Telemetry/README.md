# Procel-Telemetry

`Procel-Telemetry` recebe eventos brutos de telemetria por REST e MQTT, grava os documentos no MongoDB e, por worker separado, envia eventos elegiveis para a ingestao interna do `Procel-API`.

`Procel-Telemetry` nao acessa diretamente o PostgreSQL.

A aplicacao e publicavel de forma independente. MongoDB, MQTT e Procel-API sao
configurados por variaveis e podem estar em recursos separados, dominios
externos, servicos gerenciados ou no Compose integrado.

## Entradas

### REST

```text
POST /api/telemetry/events
```

O usuario autenticado define o `producerId`. O corpo deve ser JSON com:

```json
{
  "source": "REST",
  "messageId": "msg-001",
  "sensorId": "sensor-externo-1",
  "sourceTimestamp": "2026-08-19T08:00:00Z",
  "payload": {
    "temperature": 24.5
  }
}
```

### MQTT 5

MQTT fica desabilitado por padrao. Quando habilitado, usa QoS 1, sessao persistente, clientId configuravel e ACK manual.

Topicos assinados:

```text
procel/telemetry/v1/+/+/events
procel/telemetry/v1/+/events
```

Formato:

```text
procel/telemetry/v1/{producerId}/{sensorId}/events
procel/telemetry/v1/{producerId}/events
```

Envelope MQTT:

```json
{
  "messageId": "mqtt-msg-001",
  "sensorId": "sensor-opcional",
  "sourceTimestamp": "2026-08-19T08:00:00Z",
  "payload": {
    "temperature": 24.5
  }
}
```

O `producerId` vem exclusivamente do topico. O `sensorId` do topico prevalece; divergencia com o envelope e descarte permanente com ACK. `messageId` e obrigatorio e nao usa packet identifier MQTT.

## MongoDB

Collection:

```text
raw_telemetry_events
```

Campos principais:

- `_id`;
- `producerId`;
- `source`;
- `messageId`;
- `sensorId`;
- `sourceTimestamp`;
- `receivedAt`;
- `payload`;
- `payloadHash`;
- `status`;
- `processing`;
- `reprocessing`;
- `reprocessAudit`;
- `expiresAt`.

Indices:

- `ux_raw_telemetry_idempotency`: unico em `producerId`, `source`, `messageId`;
- `idx_raw_telemetry_sensor_received`: busca por sensor/data;
- `idx_raw_telemetry_status_received`: operacao por estado/data;
- `idx_raw_telemetry_claim`: claim atomico do worker;
- `idx_raw_telemetry_processing_lock`: recuperacao de lease;
- `idx_raw_telemetry_received`: ordenacao temporal;
- `ttl_raw_telemetry_expires_at`: TTL por `expiresAt`.

## Estados

```text
RECEIVED
PROCESSING
CANONICAL_ACCEPTED
CANONICAL_DUPLICATE
CANONICAL_CONFLICT
CANONICAL_FAILED
DISCARDED
```

## Idempotencia

A idempotencia bruta usa `producerId + source + messageId`. O fingerprint compara source, sensor, sourceTimestamp e payload. Duplicata equivalente retorna o documento existente; conflito idempotente nao persiste novo evento.

## Worker Canonico

O worker:

- fica desabilitado por padrao;
- processa lote sequencial ate `batch-size`;
- usa claim atomico `RECEIVED -> PROCESSING`;
- aplica lease por evento;
- recupera `PROCESSING` preso;
- consulta snapshot do `Procel-API` com cache TTL;
- seleciona perfil ativo sem resolver ambiguidade silenciosamente;
- chama rota interna do `Procel-API`;
- salva `profileId`, `parserVersionId` e `canonicalMeasurementId`;
- aplica retry apenas para timeout, conexao, `429` e `5xx`.

Falhas permanentes incluem `PROFILE_NOT_FOUND`, `PROFILE_AMBIGUOUS` e `SENSOR_ROUTE_REQUIRED`.

## Reprocessamento

Endpoints administrativos:

```text
GET  /api/telemetry/events
GET  /api/telemetry/events/{id}
POST /api/telemetry/events/{id}/reprocess
```

Reprocessamento exige `ADMIN`, motivo textual de 1 a 500 caracteres, preserva payload/hash/messageId/producerId/timestamps e apenas recoloca eventos elegiveis em `RECEIVED`.

## Variaveis

| Variavel | Finalidade |
| --- | --- |
| `SPRING_MONGODB_URI` / `SPRING_DATA_MONGODB_URI` | URI MongoDB com database |
| `PROCEL_TELEMETRY_MAX_PAYLOAD_BYTES` | Limite de 256 KiB por padrao |
| `PROCEL_TELEMETRY_RETENTION_DAYS` | Retencao TTL |
| `PROCEL_TELEMETRY_CANONICAL_WORKER_ENABLED` | Habilita worker |
| `PROCEL_API_BASE_URL` | Base URL do `Procel-API` |
| `PROCEL_TELEMETRY_SERVICE_JWT_SUBJECT` | Subject do JWT de servico |
| `PROCEL_TELEMETRY_SERVICE_JWT_SECRET` | Segredo do JWT de servico |
| `PROCEL_TELEMETRY_SERVICE_JWT_TTL` | TTL do JWT de servico |
| `PROCEL_TELEMETRY_MQTT_ENABLED` | Habilita MQTT |
| `PROCEL_TELEMETRY_MQTT_BROKER_URL` | Broker MQTT |
| `PROCEL_TELEMETRY_MQTT_CLIENT_ID` | ClientId persistente |
| `PROCEL_TELEMETRY_MQTT_USERNAME` | Usuario MQTT |
| `PROCEL_TELEMETRY_MQTT_PASSWORD` | Senha MQTT |
| `PROCEL_TELEMETRY_MQTT_TLS_ENABLED` | TLS |

## Execucao

```bash
docker compose -f compose.yaml up -d
../Procel-API/mvnw -f pom.xml test
../Procel-API/mvnw -f pom.xml spring-boot:run
```

`compose.yaml` sobe `Procel-Telemetry + MongoDB + MQTT` para desenvolvimento
isolado do modulo. Com `PROCEL_TELEMETRY_CANONICAL_WORKER_ENABLED=false`, a
Telemetry nao exige que a API esteja disponivel. Com
`PROCEL_TELEMETRY_MQTT_ENABLED=false`, MQTT nao impede a inicializacao.

Build independente:

```bash
docker build -t procel-telemetry .
```

Healthcheck:

```text
/actuator/health
```

No Coolify:

```text
Base directory: /Procel-Telemetry
Dockerfile: /Dockerfile
Healthcheck: /actuator/health
```

Configure `SPRING_MONGODB_URI`, `PROCEL_API_BASE_URL`, secrets JWT e variaveis
MQTT conforme a topologia. `PROCEL_API_BASE_URL` deve ser uma URL resolvivel a
partir do container da Telemetry.

Swagger:

```text
http://localhost:8081/docs
http://localhost:8081/v3/api-docs
```

## Testes

Os testes usam MongoDB Testcontainers e HiveMQ Testcontainers para REST, idempotencia, claim concorrente, MQTT, ACK/redelivery, retained, reconexao, worker e reprocessamento.
