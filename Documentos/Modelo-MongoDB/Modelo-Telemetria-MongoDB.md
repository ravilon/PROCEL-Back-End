# Modelo Documental da Telemetria no MongoDB

## Collection

```text
raw_telemetry_events
```

A collection armazena eventos brutos recebidos pelo `Procel-Telemetry` antes de qualquer ingestao canonica no `Procel-API`.

## Ciclo de Vida

```text
RECEIVED -> PROCESSING -> CANONICAL_ACCEPTED
                         -> CANONICAL_DUPLICATE
                         -> CANONICAL_CONFLICT
                         -> CANONICAL_FAILED
                         -> DISCARDED
```

Eventos reprocessaveis (`CANONICAL_FAILED`, `CANONICAL_CONFLICT`, `DISCARDED`) podem ser recolocados em `RECEIVED` por operacao administrativa. O payload bruto, hash, messageId, producerId e timestamps sao preservados.

## Documento

Campos raiz:

| Campo | Tipo | Finalidade |
| --- | --- | --- |
| `_id` | ObjectId/string | Identificador MongoDB |
| `producerId` | string | Identidade do produtor |
| `source` | `REST` ou `MQTT` | Origem do evento |
| `messageId` | string | Identidade idempotente do produtor |
| `sensorId` | string opcional | Sensor informado |
| `sourceTimestamp` | instant opcional | Timestamp do produtor |
| `receivedAt` | instant | Recebimento pelo Telemetry |
| `payload` | object | Payload bruto preservado |
| `payloadHash` | string | Fingerprint do conteudo bruto |
| `status` | enum | Estado de processamento |
| `processing` | object | Tentativas, lease e resultado canonico |
| `reprocessing` | object | Contador e ultimo pedido |
| `reprocessAudit` | array | Historico de reprocessamento |
| `expiresAt` | instant | Base do TTL |

### `processing`

| Campo | Finalidade |
| --- | --- |
| `attempts` | Tentativas do worker canonico |
| `lastAttemptAt` | Ultima tentativa |
| `nextAttemptAt` | Proxima tentativa permitida |
| `lockedAt` | Inicio do lease atual |
| `workerId` | Worker que fez claim |
| `lastError` | Ultimo erro operacional |
| `canonicalMeasurementId` | Medicao canonica criada ou reutilizada |
| `profileId` | Perfil selecionado |
| `parserVersionId` | Parser version ativo usado |

### `reprocessing`

| Campo | Finalidade |
| --- | --- |
| `count` | Quantidade de reprocessamentos |
| `lastRequestedAt` | Data do ultimo pedido |
| `lastRequestedBy` | Usuario do ultimo pedido |
| `lastReason` | Motivo do ultimo pedido |

### `reprocessAudit`

Cada entrada preserva:

- `previousStatus`;
- `lastError`;
- `attempts`;
- `canonicalMeasurementId`;
- `profileId`;
- `parserVersionId`;
- `requestedBy`;
- `requestedAt`;
- `reason`.

## Indices

| Nome | Campos | Unique | TTL | Finalidade |
| --- | --- | --- | --- | --- |
| `ux_raw_telemetry_idempotency` | `producerId`, `source`, `messageId` | Sim | Nao | Idempotencia bruta |
| `idx_raw_telemetry_sensor_received` | `sensorId`, `receivedAt desc` | Nao | Nao | Filtro administrativo por sensor |
| `idx_raw_telemetry_status_received` | `status`, `receivedAt desc` | Nao | Nao | Filtro por estado |
| `idx_raw_telemetry_claim` | `status`, `processing.nextAttemptAt`, `receivedAt` | Nao | Nao | Claim atomico do worker |
| `idx_raw_telemetry_processing_lock` | `status`, `processing.lockedAt` | Nao | Nao | Recuperacao de lease |
| `idx_raw_telemetry_received` | `receivedAt` | Nao | Nao | Ordenacao temporal |
| `ttl_raw_telemetry_expires_at` | `expiresAt` | Nao | Sim | Retencao automatica |

## Relacao com PostgreSQL

A relacao com `medicao` e `medicao_ingestao_metadata` e logica, nao uma FK fisica. O evento bruto guarda `processing.canonicalMeasurementId`; o PostgreSQL guarda contexto bruto na metadata quando a ingestao veio da rota interna da Telemetry.

## Consultas Administrativas

Exemplos:

```javascript
db.raw_telemetry_events.find({ status: "CANONICAL_FAILED" }).sort({ receivedAt: -1 }).limit(20)
db.raw_telemetry_events.find({ sensorId: "sensor-01" }).sort({ receivedAt: -1 }).limit(20)
db.raw_telemetry_events.find({ "processing.canonicalMeasurementId": "..." })
```

## Limitacoes

- Payload bruto nao deve ser alterado pelo reprocessamento.
- A collection nao substitui a medicao canonica no PostgreSQL.
- Retencao automatica aplica-se aos eventos brutos, nao aos dados canonicos.
