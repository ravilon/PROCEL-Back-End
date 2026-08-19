# MQTT no Procel-Telemetry

## Protocolo

A entrada MQTT usa MQTT 5 com Eclipse Paho v5, QoS 1 e ACK manual real. O subscriber fica desabilitado por padrao.

## Topicos

Filtros assinados:

```text
procel/telemetry/v1/+/+/events
procel/telemetry/v1/+/events
```

Formatos aceitos:

```text
procel/telemetry/v1/{producerId}/{sensorId}/events
procel/telemetry/v1/{producerId}/events
```

`producerId` vem exclusivamente do topico. Quando o topico possui `sensorId`, ele prevalece. Se o envelope tambem informar `sensorId` diferente, a mensagem e descartada como erro permanente.

## Envelope JSON

```json
{
  "messageId": "mqtt-20260819-0001",
  "sensorId": "sensor-01",
  "sourceTimestamp": "2026-08-19T08:00:00Z",
  "payload": {
    "temperature": 24.5,
    "humidity": 61.2
  }
}
```

Campos:

| Campo | Obrigatorio | Observacao |
| --- | --- | --- |
| `messageId` | Sim | Identidade persistente do evento. Nao e o packet identifier MQTT. |
| `sensorId` | Nao | Obrigatorio apenas quando o topico nao inclui sensor e o roteamento posterior exigir sensor. |
| `sourceTimestamp` | Nao | Timestamp informado pelo produtor. |
| `payload` | Sim | Objeto bruto preservado no MongoDB. |

`source` e sempre `MQTT` e nao vem do envelope.

## ACK, Retained e Falhas

O ACK manual ocorre somente depois de:

- persistencia bem-sucedida;
- duplicata equivalente;
- conflito idempotente registrado;
- descarte permanente.

Nao ha ACK quando ocorre falha transitoria do MongoDB, permitindo redelivery depois de reconexao.

Mensagens retained sao rejeitadas por padrao e confirmadas com ACK. O limite de payload e aplicado antes do parsing JSON, com padrao de 256 KiB.

Erros permanentes com ACK:

- JSON invalido;
- `messageId` ausente ou invalido;
- topico invalido;
- sensor divergente entre topico e envelope;
- payload acima do limite;
- retained quando `reject-retained=true`.

## Sessao e Reconexao

O clientId e configuravel. A sessao persistente usa `cleanStart=false` e `sessionExpiryInterval` configuravel. A reconexao automatica e habilitavel por propriedade, e o subscriber reassina os dois filtros apos reconectar.

## TLS e Credenciais

Configure TLS, truststore, keystore, usuario e senha por properties/env. Senhas, secrets, tokens e payload completo nao devem aparecer em logs.

Variaveis principais:

```text
PROCEL_TELEMETRY_MQTT_ENABLED
PROCEL_TELEMETRY_MQTT_BROKER_URL
PROCEL_TELEMETRY_MQTT_CLIENT_ID
PROCEL_TELEMETRY_MQTT_USERNAME
PROCEL_TELEMETRY_MQTT_PASSWORD
PROCEL_TELEMETRY_MQTT_TLS_ENABLED
```

## Exemplo Local

```bash
mosquitto_pub -h localhost -p 1883 -q 1 \
  -t procel/telemetry/v1/produtor-a/sensor-01/events \
  -m '{"messageId":"mqtt-20260819-0001","sourceTimestamp":"2026-08-19T08:00:00Z","payload":{"temperature":24.5}}'
```

## Fluxo ate o PostgreSQL

```text
MQTT -> Procel-Telemetry -> MongoDB raw_telemetry_events -> worker canonico -> Procel-API -> PostgreSQL
```

`Procel-Telemetry` nao faz parsing canonico e nao acessa PostgreSQL.

## ACL Recomendada

No broker, restrinja cada produtor aos topicos do proprio `producerId`, por exemplo:

```text
write procel/telemetry/v1/produtor-a/+/events
write procel/telemetry/v1/produtor-a/events
```

Essa ACL complementa a regra de que a identidade do produtor vem do topico.
