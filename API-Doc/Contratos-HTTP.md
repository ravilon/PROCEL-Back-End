# Contratos HTTP

## Procel-API

Base local: `http://localhost:8080`

| Endpoint | Metodo | Roles | Sucesso | Observacoes |
| --- | --- | --- | --- | --- |
| `/actuator/health` | GET | publica | `200` | Health |
| `/api/auth/login` | POST | publica | `200` | Retorna JWT |
| `/api/auth/register` | POST | autenticado conforme seguranca | `201` | Cadastro |
| `/api/sensors/ingest` | POST | `ADMIN`, `INGESTOR` | `201`, `200` | Ingestao canonica; `200` para duplicata equivalente |
| `/api/sensors/ingest/mock` | POST | `ADMIN`, `INGESTOR` | `201`, `200` | Ingestao mock |
| `/api/sensors/{sensorExternalId}/medicoes` | GET | autenticado | `200` | Consulta paginada/filtrada |
| `/api/sensors/{sensorExternalId}/medicoes/latest` | GET | autenticado | `200` | Ultima medicao |
| `/api/rooms/{compartimentoId}/medicoes` | GET | autenticado | `200` | Medicoes por compartimento |
| `/api/rooms/{compartimentoId}/medicoes/latest` | GET | autenticado | `200` | Ultimas por compartimento |
| `/api/sensor-integrations/profiles` | GET/POST | `ADMIN` | `200`, `201` | Perfis de integracao |
| `/api/sensor-integrations/profiles/{profileId}` | GET/PUT/DELETE | `ADMIN` | `200`, `204` | Detalhe/edicao/remocao |
| `/api/sensor-integrations/profiles/{profileId}/activate` | POST | `ADMIN` | `200` | Ativacao de perfil |
| `/api/sensor-integrations/profiles/{profileId}/versions` | GET/POST | `ADMIN` | `200`, `201` | Parser versions |
| `/api/sensor-integrations/profiles/{profileId}/versions/{versionId}` | GET/PUT | `ADMIN` | `200` | Detalhe/edicao |
| `/api/sensor-integrations/profiles/{profileId}/versions/{versionId}/activate` | POST | `ADMIN` | `200` | Ativa versao |
| `/api/sensor-integrations/bindings` | GET/POST | `ADMIN` | `200`, `201` | Bindings |
| `/api/sensor-integrations/bindings/{bindingId}/activate` | POST | `ADMIN` | `200` | Ativa binding |
| `/api/sensor-integrations/bindings/{bindingId}` | DELETE | `ADMIN` | `204` | Remove binding |
| `/api/sensor-integrations/snapshot` | GET | `ADMIN`, `INGESTOR`, `TELEMETRY_SERVICE` | `200` | Snapshot ativo |
| `/api/sensors/ingest/integrations/{profileId}` | POST | `ADMIN`, `INGESTOR` | `201`, `200` | Perfil `PAYLOAD_POINTER` |
| `/api/sensors/{sensorExternalId}/ingest/integrations/{profileId}` | POST | `ADMIN`, `INGESTOR` | `201`, `200` | Perfil `ROUTE_SENSOR` |
| `/api/sensors/internal/telemetry-events/ingest/integrations/{profileId}` | POST | `TELEMETRY_SERVICE` | `201`, `200` | Rota interna Telemetry sem sensor na rota |
| `/api/sensors/internal/telemetry-events/{sensorExternalId}/ingest/integrations/{profileId}` | POST | `TELEMETRY_SERVICE` | `201`, `200` | Rota interna Telemetry com sensor na rota |
| `/api/analytics/aggregation-jobs` | POST | `ADMIN`, `OPERADOR` | `202` | Cria ou retorna job equivalente |
| `/api/analytics/aggregation-jobs/{id}` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Estado e progresso |
| `/api/analytics/numeric-buckets` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Lista buckets persistidos e paginados; filtros existentes mas sem intersecao retornam pagina vazia |
| `/api/analytics/numeric-buckets/summary` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Consolida somente buckets persistidos; media ponderada por `sampleCount` |

Erros comuns: `400` validacao, `401` ausente/invalido, `403` role insuficiente, `404` recurso inexistente, `409` conflito idempotente ou de estado, `422` payload semanticamente invalido.

## DTOs Principais

### Ingestao Canonica

```json
{
  "messageId": "msg-001",
  "sensorExternalId": "sensor-temp-101",
  "timestamp": "2026-08-19T08:00:00Z",
  "source": "REST",
  "sourceReceivedAt": "2026-08-19T08:00:03Z",
  "values": [
    {
      "parameter": "temperature",
      "numericValue": 24.5
    }
  ]
}
```

### Rota Interna Telemetry

```json
{
  "rawTelemetryEventId": "66c4f1b2a7f4a73d2c95d001",
  "originalProducerId": "building-a-gateway",
  "rawMessageId": "mqtt-20260819-0001",
  "rawReceivedAt": "2026-08-19T08:00:03Z",
  "rawSourceTimestamp": "2026-08-19T08:00:00Z",
  "payload": {
    "temperature": 24.5
  }
}
```

Chave idempotente interna:

```text
integration_profile_id + sensor_external_id + original_producer_id + raw_message_id
```

### Job de Agregacao

```json
{
  "from": "2026-08-19T08:00:00Z",
  "to": "2026-08-19T09:00:00Z",
  "windowDuration": "PT15M",
  "sensorExternalId": "sensor-temp-101",
  "compartimentoId": "room-101"
}
```

### Consulta de Buckets Numericos

`from` e `to` sao obrigatorios, `from < to`, e o periodo maximo e configurado em
`procel.analytics.buckets.max-period`. `page` deve ser maior ou igual a zero e
`size` deve ficar entre `1` e `procel.analytics.buckets.max-page-size`.
`aggregationVersion`, quando informado, deve ser positiva.

O filtro temporal retorna buckets integralmente contidos no intervalo:
`bucketStart >= from` e `bucketEnd <= to`. Buckets parcialmente sobrepostos nao sao
fracionados nem usados no resumo, para evitar estatisticas parciais incorretas.

Filtros por `sensorExternalId`, `parametroDefId` e `compartimentoId` inexistentes
retornam `422`. Filtros existentes, porem incompatíveis entre si, retornam lista
vazia de forma consistente.

O endpoint `/api/analytics/numeric-buckets/summary` nao consulta `medicao` nem
`parametro_valor`; ele consolida os buckets persistidos usando media ponderada por
`sampleCount`, menor `minimumValue`, maior `maximumValue` e soma de `sampleCount`.

### Consumo no Procel-Admin

A rota `/analiticos` esta disponivel para `ADMIN`, `OPERADOR` e `ANALISTA`.
`USUARIO` e `INGESTOR` nao veem o item `Análises` no menu e nao acessam a rota.

A tela envia somente parametros preenchidos para `/api/analytics/numeric-buckets`
e `/api/analytics/numeric-buckets/summary`. `page` e `size` sao enviados apenas
para a listagem paginada. O summary alimenta cards por grupo retornado, sem
misturar unidades, parametros, sensores ou versoes diferentes. O grafico temporal
usa `averageValue` dos buckets da pagina atual; uma consulta especifica para
series longas fica pendente para a etapa 12.

Os valores decimais chegam como JSON number por serializacao de `BigDecimal`.
O frontend usa esses numeros para exibicao e grafico, sem persistir calculos
derivados e sem recalcular media consolidada.

## Procel-Telemetry

Base local: `http://localhost:8081`

| Endpoint | Metodo | Roles | Sucesso | Observacoes |
| --- | --- | --- | --- | --- |
| `/actuator/health` | GET | publica | `200` | Health |
| `/api/telemetry/events` | POST | `ADMIN`, `INGESTOR` | `201`, `200` | Ingestao bruta REST |
| `/api/telemetry/events` | GET | `ADMIN` | `200` | Listagem server-side com filtros |
| `/api/telemetry/events/{id}` | GET | `ADMIN` | `200` | Detalhe bruto |
| `/api/telemetry/events/{id}/reprocess` | POST | `ADMIN` | `200` | Recoloca evento elegivel em `RECEIVED` |

### Ingestao Bruta REST

```json
{
  "source": "REST",
  "messageId": "rest-msg-001",
  "sensorId": "sensor-temp-101",
  "sourceTimestamp": "2026-08-19T08:00:00Z",
  "payload": {
    "temperature": 24.5
  }
}
```

### Reprocessamento

```json
{
  "reason": "Reprocessar apos correcao de binding"
}
```

`reason` e obrigatorio, trim, de 1 a 500 caracteres.

## Swagger

OpenAPI gerado em runtime:

```text
Procel-API:       /v3/api-docs
Procel-Telemetry: /v3/api-docs
```

## Deploy

O projeto suporta dois modos oficiais.

Modo integrado pela raiz:

```bash
docker compose up -d
```

Sobe PostgreSQL, MongoDB, MQTT, Procel-API, Procel-Telemetry e Procel-Admin. Os
hostnames internos do Compose aparecem apenas como defaults desse modo.

Modo independente:

```bash
docker build -t procel-api ./Procel-API
docker build -t procel-telemetry ./Procel-Telemetry
docker build -t procel-admin ./Procel-Admin
```

Cada imagem aceita dependencias externas por variaveis de ambiente e possui
healthcheck proprio: API e Telemetry em `/actuator/health`, Admin em `/healthz`.
No Coolify, publique cada modulo com seu diretorio base e configure CORS para o
dominio externo do Admin.
