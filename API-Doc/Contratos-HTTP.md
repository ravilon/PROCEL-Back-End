# Contratos HTTP

## Proben-API

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
| `/api/analytics/numeric-buckets` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Lista buckets persistidos; filtros por sensor, modelo, parâmetro e compartimento |
| `/api/analytics/numeric-buckets/summary` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Consolida somente buckets persistidos; media ponderada por `sampleCount` |
| `/api/analytics/people-points` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Lista até 200 pessoas e saldos agregados do ledger; aceita busca textual `q` |
| `/api/shared-contexts` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Lista contextos compartilhados |
| `/api/shared-contexts` | POST | `ADMIN` | `201` | Cria contexto e referencia geografica propria ou de campus |
| `/api/shared-contexts/{id}` | GET/PUT/DELETE | Leitura: `ADMIN`, `OPERADOR`, `ANALISTA`; escrita: `ADMIN` | `200`, `204` | Contexto compartilhado e preview de exclusao em `/{id}/delete-preview` |
| `/api/shared-contexts/{id}/integration` | PUT | `ADMIN` | `200` | Configura coleta HTTP, mapeamentos e credencial gerenciada |
| `/api/shared-contexts/{id}/integration/test` | POST | `ADMIN` | `200` | Testa configuracao sem persistir observacao |
| `/api/shared-contexts/{id}/integrations` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Lista integracoes de coleta |
| `/api/shared-contexts/integrations/{id}/collect` | POST | `ADMIN` | `200` | Executa coleta manual e ingestao canonica |
| `/api/shared-contexts/integrations/{id}/test` | POST | `ADMIN` | `200` | Executa teste de coleta |
| `/api/shared-contexts/integrations/{id}/activate` | POST | `ADMIN` | `200` | Ativa coleta |
| `/api/shared-contexts/integrations/{id}/deactivate` | POST | `ADMIN` | `200` | Desativa coleta |
| `/api/shared-contexts/rooms/{roomId}/observations/latest` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Observacoes externas recentes do ambiente |
| `/api/shared-contexts/rooms/{roomId}/parameters/{parameterId}/latest` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Observacao mais recente para parametro |
| `/api/shared-contexts/rooms/{roomId}/parameters/{parameterId}/average-7d` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Media da janela recente de sete dias |
| `/api/environment-rules` | GET/POST | Consulta: `ADMIN`, `OPERADOR`, `ANALISTA`; escrita: `ADMIN` | `200`, `201` | Regras ambientais por escopo |
| `/api/environment-rules/{id}` | GET/PUT/DELETE | Consulta: `ADMIN`, `OPERADOR`, `ANALISTA`; escrita: `ADMIN` | `200`, `204` | Leitura, atualizacao e remocao |
| `/api/environment-rules/{id}/activate` | POST | `ADMIN` | `200` | Ativa regra |
| `/api/environment-rules/{id}/deactivate` | POST | `ADMIN` | `200` | Desativa regra |
| `/api/environment-rules/{id}/test` | POST | `ADMIN` | `200` | Avalia sem executar efeitos |
| `/api/environment-rules/{id}/execute` | POST | `ADMIN` | `200` | Execucao manual |
| `/api/environment-rules/executions` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Historico de execucoes |
| `/api/environment-rules/resolution/{roomId}` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Resolve regra aplicavel ao ambiente |
| `/api/campuses` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Lista campi e coordenadas |
| `/api/campuses/{nome}` | PUT | `ADMIN` | `200` | Atualiza coordenadas do campus |
| `/api/rooms/sync` | POST | Autenticado | `200` | Sincroniza estrutura de salas da fonte configurada |
| `/api/rooms/aulas/sync` | POST | Operacao administrativa | `202` | Inicia sincronizacao assincrona de aulas |
| `/api/rooms/aulas/sync/{jobId}` | GET | Operacao administrativa | `200` | Consulta job de sincronizacao |
| `/api/missions/{missionId}/events` | GET/POST | `ADMIN`, `OPERADOR`, `ANALISTA` para consulta; `ADMIN` para escrita | `200`, `201` | Eventos configuraveis de missoes |
| `/api/mission-events/{eventId}` | GET/PUT/DELETE | `ADMIN`, `OPERADOR`, `ANALISTA` para consulta; `ADMIN` para escrita | `200`, `204` | Detalhe, edicao e remocao logica de evento |
| `/api/mission-events/{eventId}/conditions` | POST | `ADMIN` | `201` | Cria condicao de evento |
| `/api/mission-events/{eventId}/conditions/{conditionId}` | PUT/DELETE | `ADMIN` | `200`, `204` | Edita ou remove condicao |
| `/api/admin/missions/events` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Consulta operacional de eventos |
| `/api/admin/missions/evaluation-requests` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Requests do motor de eventos |
| `/api/admin/missions/windows` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Janelas temporais paginadas |
| `/api/admin/missions/windows/{windowId}` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Detalhe de janela temporal |
| `/api/admin/missions/windows/{windowId}/evidences` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Evidencias de janela |
| `/api/admin/missions/windows/{windowId}/retry` | POST | `ADMIN` | `200` | Reagenda janela elegivel |
| `/api/admin/missions/windows/{windowId}/satisfy` | POST | `ADMIN` | `200` | Marca janela como satisfeita de forma operacional |
| `/api/admin/missions/windows/{windowId}/invalidate` | POST | `ADMIN` | `200` | Invalida janela |
| `/api/admin/missions/windows/{windowId}/expire` | POST | `ADMIN` | `200` | Expira janela |
| `/api/admin/missions/windows/{windowId}/fail` | POST | `ADMIN` | `200` | Marca janela como failed |
| `/api/admin/missions/occurrences` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Ocorrencias paginadas |
| `/api/admin/missions/occurrences/{occurrenceId}` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Detalhe de ocorrencia |
| `/api/admin/missions/occurrences/{occurrenceId}/evidences` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Evidencias de ocorrencia |
| `/api/admin/missions/occurrences/{occurrenceId}/status` | POST | `ADMIN` | `200` | Transicao operacional de status |
| `/api/admin/missions/workers/status` | GET | `ADMIN`, `OPERADOR`, `ANALISTA` | `200` | Flags, backlog e estado dos workers |
| `/api/admin/missions/workers/evaluation/run` | POST | `ADMIN` | `200` | Execucao manual do worker de avaliacao |
| `/api/admin/missions/workers/temporal-windows/run` | POST | `ADMIN` | `200` | Execucao manual do worker temporal |
| `/api/pessoas/{pessoaId}/pontos` | GET | `ADMIN`, `OPERADOR` ou proprio usuario | `200` | Saldo calculado pelo ledger |
| `/api/pessoas/{pessoaId}/pontos/lancamentos` | GET | `ADMIN`, `OPERADOR` ou proprio usuario | `200` | Extrato paginado e ordenado |

`GET /api/analytics/people-points?q=texto` lista ate 200 pessoas, ordenadas por
saldo decrescente, com nome, identificador, e-mail e totais agregados do ledger.
O parametro `q` e opcional e pesquisa nome, ID ou e-mail. A tela Analytics usa
essa resposta para a tabela de pontos e consulta o extrato individual somente
quando uma pessoa e selecionada.

A resposta de saldo usa `saldoTotalPontos`, `totalConcedidoPontos`,
`totalEstornadoPontos` e `totalAjustadoPontos`. Cada item do extrato expõe o
montante como `pontos`. O catálogo de missões também recebe e retorna `pontos`
no lugar do antigo campo `value`. O ledger é `pontos_lancamento`; saldo é
calculado pela soma dos lançamentos.

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
`proben.analytics.buckets.max-period`. `page` deve ser maior ou igual a zero e
`size` deve ficar entre `1` e `proben.analytics.buckets.max-page-size`.
`aggregationVersion`, quando informado, deve ser positiva.

O filtro temporal retorna buckets integralmente contidos no intervalo:
`bucketStart >= from` e `bucketEnd <= to`. Buckets parcialmente sobrepostos nao sao
fracionados nem usados no resumo, para evitar estatisticas parciais incorretas.

Filtros por `sensorExternalId`, `parametroDefId` e `compartimentoId` inexistentes
retornam `422`. `sensorTypeName` filtra pelo nome do modelo/tipo cadastrado em
`tipo_de_sensor`. Filtros existentes, porem incompatíveis entre si, retornam lista
vazia de forma consistente. É possível consultar por parâmetro ou modelo sem
informar um sensor individual ou compartimento.

Buckets não são criados durante a ingestão canônica. É necessário criar um job em
`/api/analytics/aggregation-jobs` e manter ativo o worker de agregação
(`PROBEN_ANALYTICS_AGGREGATION_WORKER_ENABLED=true`). Sem jobs processados, as
consultas retornam listas vazias mesmo quando há medições canônicas no período.

Exemplos de consulta por uma unica dimensao:

```http
GET /api/analytics/numeric-buckets?from=2026-11-12T00:00:00Z&to=2026-11-13T00:00:00Z&parametroDefId=bd2a1394-bd4d-4991-a0b1-d225016a8a57
GET /api/analytics/numeric-buckets?from=2026-11-12T00:00:00Z&to=2026-11-13T00:00:00Z&sensorTypeName=TCC_DEMO_SENSOR_20261112-02
```

O endpoint `/api/analytics/numeric-buckets/summary` nao consulta `medicao` nem
`parametro_valor`; ele consolida os buckets persistidos usando media ponderada por
`sampleCount`, menor `minimumValue`, maior `maximumValue` e soma de `sampleCount`.

### Consumo no Proben-Admin

A rota `/analiticos` esta disponivel para `ADMIN`, `OPERADOR` e `ANALISTA`.
`USUARIO` e `INGESTOR` nao veem o item `Análises` no menu e nao acessam a rota.

A tela envia somente parametros preenchidos para `/api/analytics/numeric-buckets`
e `/api/analytics/numeric-buckets/summary`. `page` e `size` sao enviados apenas
para a listagem paginada. O summary alimenta cards por grupo retornado, sem
misturar unidades, parametros, sensores ou versoes diferentes. O grafico temporal
usa `averageValue` dos buckets da pagina atual; a consulta de series extensas e
limitada pela paginacao atual do endpoint.

Observacoes compartilhadas sao consultadas por `/api/shared-contexts/rooms/...`.
A tela dedicada permite filtrar salas e parametros sem expor credenciais dos
provedores. Coletas e regras ambientais possuem configuracao e auditoria propria;
seus contratos completos estao no OpenAPI gerado, incluindo campos e validacoes.

Os valores decimais chegam como JSON number por serializacao de `BigDecimal`.
O frontend usa esses numeros para exibicao e grafico, sem persistir calculos
derivados e sem recalcular media consolidada.

### Motor de Missoes

Eventos instantaneos usam `SimpleMissionRuleEngine` por padrao. Janelas temporais
e Drools permanecem opt-in por configuracao. Ocorrencias confirmadas registram
snapshot e evidencias; os efeitos em atividades, progresso, conclusao automatica
e pontos sao idempotentes e usam constraints no PostgreSQL.

`pontos_lancamento` e append-only. O saldo retornado por `/api/pessoas/{pessoaId}/pontos`
e calculado pela soma de concessoes, estornos e ajustes existentes no ledger.

## Proben-Telemetry

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

OpenAPI gerado em runtime e fonte de verdade para parametros, schemas, validacoes
e rotas operacionais nao detalhadas nesta tabela. Os arquivos Postman/Insomnia sao
colecoes de smoke/regressao, nao inventario completo do contrato.

```text
Proben-API:       /v3/api-docs
Proben-Telemetry: /v3/api-docs
```

## Deploy

O projeto suporta dois modos oficiais.

Modo integrado pela raiz:

```bash
docker compose up -d
```

Sobe PostgreSQL, MongoDB, MQTT, Proben-API, Proben-Telemetry e Proben-Admin. Os
hostnames internos do Compose aparecem apenas como defaults desse modo.

Modo independente:

```bash
docker build -t proben-api ./Proben-API
docker build -t proben-telemetry ./Proben-Telemetry
docker build -t proben-admin ./Proben-Admin
```

Cada imagem aceita dependencias externas por variaveis de ambiente e possui
healthcheck proprio: API e Telemetry em `/actuator/health`, Admin em `/healthz`.
No Coolify, publique cada modulo com seu diretorio base e configure CORS para o
dominio externo do Admin.
