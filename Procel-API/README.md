# Procel-API

`Procel-API` e a API principal do PROCEL. Ela mantem o dominio canonico no PostgreSQL, autentica usuarios, recebe medicoes, administra sensores, perfis de integracao e executa jobs analiticos por janela.

A aplicacao e publicavel de forma independente. Ela depende somente de
PostgreSQL acessivel por `SPRING_DATASOURCE_URL` e nao depende de Admin,
Telemetry, MongoDB, MQTT ou do Compose integrado.

## Responsabilidades

- Autenticacao JWT de usuarios e servicos.
- Cadastro e consulta de pessoas, cursos, disciplinas, periodos, presencas, sensores e regras.
- Ingestao canonica idempotente de medicoes.
- Ingestao por perfis de integracao, parsers, versoes ativas e bindings.
- Rotas internas exclusivas para eventos vindos do `Procel-Telemetry`.
- Snapshot de integracao usado pelo worker canonico.
- Jobs assincronos de agregacao por periodo.
- Persistencia de buckets numericos em `analytics_numeric_bucket`.

## PostgreSQL e Flyway

O schema e criado por migrations em `src/main/resources/db/migration`. Migrations antigas nao devem ser editadas. As migrations recentes incluem:

- `V16`: perfis, parser versions, bindings e snapshot.
- `V17`: metadata interna para eventos brutos do Telemetry.
- `V18`: jobs e janelas de agregacao.
- `V19`: buckets numericos analiticos.

Embora `spring.jpa.hibernate.ddl-auto=update` ainda esteja no `application.yml`, Flyway e a fonte de verdade do schema.

## Ingestao Canonica

Rotas principais:

```text
POST /api/sensors/ingest
POST /api/sensors/ingest/mock
GET  /api/sensors/{sensorExternalId}/medicoes
GET  /api/sensors/{sensorExternalId}/medicoes/latest
GET  /api/rooms/{compartimentoId}/medicoes
GET  /api/rooms/{compartimentoId}/medicoes/latest
```

O DTO canonico contem `messageId`, `sensorExternalId`, `timestamp`, `source`, `sourceReceivedAt` e `values`. A metadata de ingestao preserva contexto, idempotencia, produtor, timestamps e vinculo com eventos brutos quando a origem e o `Procel-Telemetry`.

## Integracoes

Rotas administrativas:

```text
GET/POST /api/sensor-integrations/profiles
GET/PUT/DELETE /api/sensor-integrations/profiles/{profileId}
POST /api/sensor-integrations/profiles/{profileId}/activate
POST/GET /api/sensor-integrations/profiles/{profileId}/versions
GET/PUT /api/sensor-integrations/profiles/{profileId}/versions/{versionId}
POST /api/sensor-integrations/profiles/{profileId}/versions/{versionId}/activate
POST/GET /api/sensor-integrations/bindings
POST /api/sensor-integrations/bindings/{bindingId}/activate
DELETE /api/sensor-integrations/bindings/{bindingId}
GET /api/sensor-integrations/snapshot
```

Rotas publicas por perfil:

```text
POST /api/sensors/ingest/integrations/{profileId}
POST /api/sensors/{sensorExternalId}/ingest/integrations/{profileId}
```

Rotas internas exclusivas do `TELEMETRY_SERVICE`:

```text
POST /api/sensors/internal/telemetry-events/ingest/integrations/{profileId}
POST /api/sensors/internal/telemetry-events/{sensorExternalId}/ingest/integrations/{profileId}
```

Nessas rotas internas, `producerId` vem de `Authentication.getName()`. `originalProducerId`, `rawMessageId`, `rawTelemetryEventId`, `rawReceivedAt` e `rawSourceTimestamp` ficam preservados separadamente na metadata.

## Agregacoes Assincronas

Endpoints:

```text
POST /api/analytics/aggregation-jobs
GET  /api/analytics/aggregation-jobs/{id}
```

Criacao retorna `202 Accepted`. Jobs equivalentes usam chave idempotente deterministica e retornam o job existente. O periodo e dividido em janelas sem sobreposicao; a ultima janela pode ser menor.

O worker de agregacao e configuravel e desabilitado por padrao. Ele faz claim atomico de janelas, aplica lease, retry e backoff, e marca progresso no job. Jobs presos em `PROCESSING` podem ser retomados por timeout de lease.

## Buckets Numericos

`analytics_numeric_bucket` armazena agregados numericos por janela:

- `sensor_external_id`;
- `parametro_def_id`;
- `compartimento_id`;
- `bucket_start`;
- `bucket_end`;
- `average_value`;
- `minimum_value`;
- `maximum_value`;
- `sample_count`;
- `aggregation_version`;
- auditoria de job/janela de origem.

A identidade logica e:

```text
sensor_external_id + parametro_def_id + bucket_start + bucket_end + aggregation_version
```

A agregacao usa intervalo semiaberto e ignora `numericValue` nulo, booleanos e textos.

## Seguranca

Principais permissoes:

| Recurso | Roles |
| --- | --- |
| Ingestao canonica e por perfil | `ADMIN`, `INGESTOR` |
| Rotas internas Telemetry | `TELEMETRY_SERVICE` |
| Snapshot | `ADMIN`, `INGESTOR`, `TELEMETRY_SERVICE` |
| Criacao de jobs analiticos | `ADMIN`, `OPERADOR` |
| Consulta de jobs analiticos | `ADMIN`, `OPERADOR`, `ANALISTA` |

## Variaveis

| Variavel | Finalidade |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Senha PostgreSQL |
| `PROCEL_JWT_SECRET` | Segredo JWT |
| `PROCEL_TELEMETRY_SERVICE_JWT_SECRET` | Alias aceito para o segredo usado por JWT de servico quando `PROCEL_JWT_SECRET` nao for definido |
| `PROCEL_JWT_EXPIRATION_MINUTES` | TTL JWT de usuario |
| `PROCEL_BOOTSTRAP_ADMIN_EMAIL` | Admin inicial |
| `PROCEL_BOOTSTRAP_ADMIN_PASSWORD` | Senha do admin inicial |
| `PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS |
| `PROCEL_ANALYTICS_AGGREGATION_WORKER_ENABLED` | Habilita worker analitico |
| `PROCEL_ANALYTICS_AGGREGATION_POLL_INTERVAL` | Intervalo de polling |
| `PROCEL_ANALYTICS_AGGREGATION_LEASE_TIMEOUT` | Timeout do lease |
| `PROCEL_ANALYTICS_AGGREGATION_MAX_ATTEMPTS` | Tentativas por janela |
| `PROCEL_ANALYTICS_AGGREGATION_BACKOFF` | Backoff por tentativa |
| `PROCEL_ANALYTICS_AGGREGATION_BATCH_SIZE` | Janelas por ciclo |
| `PROCEL_ANALYTICS_AGGREGATION_VERSION` | Versao dos buckets |

## Execucao

```bash
docker compose -f compose.yaml up -d
./mvnw test
./mvnw spring-boot:run
```

`compose.yaml` sobe `Procel-API + PostgreSQL` para desenvolvimento isolado do
modulo. Para usar apenas PostgreSQL local, suba somente o servico `postgres`.

Build independente:

```bash
docker build -t procel-api .
```

Healthcheck:

```text
/actuator/health
```

No Coolify:

```text
Base directory: /Procel-API
Dockerfile: /Dockerfile
Healthcheck: /actuator/health
```

Configure `SPRING_DATASOURCE_URL`, usuario, senha, `PROCEL_JWT_SECRET` e CORS
com valores da plataforma. O PostgreSQL pode estar em outro container, servico
gerenciado ou outro host.

Swagger:

```text
http://localhost:8080/docs
http://localhost:8080/v3/api-docs
```

## Testes

Os testes cobrem ingestao, idempotencia, seguranca, migrations, integracoes, rotas internas, jobs de agregacao, claim concorrente, lease, retry e buckets numericos com PostgreSQL via Testcontainers.
