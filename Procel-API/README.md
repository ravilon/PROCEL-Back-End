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
- Catalogo, avaliacao e operacao de eventos de missoes.
- Janelas temporais persistentes para eventos `DURACAO`, `TRANSICAO` e `JANELA_ENCERRADA`.
- Beneficiarios academicos, atividades ciclicas, progresso e ledger append-only de XP quando as flags correspondentes estao habilitadas.

## PostgreSQL e Flyway

O schema e criado por migrations em `src/main/resources/db/migration`. Migrations antigas nao devem ser editadas. As migrations recentes incluem:

- `V16`: perfis, parser versions, bindings e snapshot.
- `V17`: metadata interna para eventos brutos do Telemetry.
- `V18`: jobs e janelas de agregacao.
- `V19`: buckets numericos analiticos.
- `V20`: catalogo persistente de definicoes de eventos de missoes.
- `V21`: requests, ocorrencias e evidencias de eventos.
- `V22`: atividades ciclicas e vinculos `atividade_evento`.
- `V23`: ledger append-only de XP.
- `V24`: janelas temporais de avaliacao e evidencias.
- `V25`: evidencias `ANTES`/`DEPOIS` para transicoes.

Embora `spring.jpa.hibernate.ddl-auto=update` ainda esteja no `application.yml`, Flyway e a fonte de verdade do schema.

## Contexto Academico

`AcademicContextResolver` e a primeira fundacao local para identificar contexto
academico a partir de um compartimento e um instante. Ele resolve o
`PeriodoAula` vigente e retorna pessoas academicamente elegiveis por
`AlunoDisciplina`, considerando disciplina, turma e status `ATIVA`.

Quando nao ha aula no instante consultado, o resultado e vazio. Quando mais de
um `PeriodoAula` atende ao mesmo compartimento/instante, a resolucao falha com
conflito para evitar escolha silenciosa de aulas sobrepostas ou ambiguas.

`PeriodoAula` nao armazena `periodo_letivo`. Nesta etapa, o `periodoLetivo`
do contexto resolvido vem dos vinculos `AlunoDisciplina` elegiveis. Se houver
mais de um periodo letivo ativo para a mesma disciplina e turma, a resolucao
falha com conflito porque nao ha informacao historica suficiente para escolher
um periodo com seguranca.

Para resolver aulas historicas corretamente, a evolucao recomendada e criar
uma entidade propria `PeriodoLetivo`, com vigencia/calendario academico, e
relacionar a ocorrencia da aula ao periodo por regra temporal explicita. Essa
entidade nao faz parte desta primeira fundacao.

O motor de eventos usa esse contexto para resolver beneficiarios academicos
quando a politica do evento permite. O snapshot da ocorrencia preserva o
contexto usado no processamento para evitar recalculo silencioso de matriculas
atuais em retries ou processamento tardio.

## Catalogo De Eventos De Missoes

`EventoDefinicao` permite configurar eventos vinculados a uma `Missao`.
`EventoCondicao` descreve as condicoes do evento e referencia `ParametroDef`
por FK. O operador das condicoes reutiliza `RegraOperador`, porque a semantica
e a mesma dos comparadores ja usados em regras de parametros: comparacoes
numericas, igualdade/diferenca booleana e comparacoes textuais.

O modo instantaneo continua usando `SimpleMissionRuleEngine` por padrao. O
Drools e opt-in e reservado ao fluxo temporal quando
`procel.missions.evaluation.temporal-windows.drools-enabled=true`.

Eventos `MEDICAO_RECEBIDA + INSTANTANEO` geram requests idempotentes e
ocorrencias confirmadas quando suas condicoes sao satisfeitas. Eventos
`DURACAO`, `TRANSICAO` e `JANELA_ENCERRADA` usam janelas temporais persistidas,
lease e evidencias. Ocorrencias confirmadas podem gerar atividades, progresso,
conclusao automatica e XP, respeitando as flags do fluxo temporal.

Endpoints administrativos:

```text
POST   /api/missions/{missionId}/events
GET    /api/missions/{missionId}/events
GET    /api/mission-events/{eventId}
PUT    /api/mission-events/{eventId}
DELETE /api/mission-events/{eventId}
POST   /api/mission-events/{eventId}/conditions
PUT    /api/mission-events/{eventId}/conditions/{conditionId}
DELETE /api/mission-events/{eventId}/conditions/{conditionId}
```

`ADMIN` pode criar, editar, ativar, desativar e remover logicamente eventos.
`OPERADOR` e `ANALISTA` podem consultar. Eventos ligados a missao inativa podem
ser editados, mas nao ativados.

## Motor De Missoes

O worker de avaliacao fica desabilitado por padrao. Quando habilitado, ele
processa `EventoAvaliacaoRequest` com claim atomico, lease, retry e conclusao
somente depois de processar os efeitos da ocorrencia.

Flags principais:

| Propriedade | Default | Efeito |
| --- | --- | --- |
| `procel.missions.rule-engine` | `simple` | Mantem `SimpleMissionRuleEngine` como padrao; `drools` e opt-in |
| `procel.missions.evaluation.worker-enabled` | `false` | Habilita worker de requests instantaneos |
| `procel.missions.evaluation.temporal-windows.enabled` | `false` | Habilita criacao/atualizacao de janelas temporais |
| `procel.missions.evaluation.temporal-windows.worker-enabled` | `false` | Habilita worker temporal |
| `procel.missions.evaluation.temporal-windows.drools-enabled` | `false` | Usa Drools no fluxo temporal |
| `procel.missions.evaluation.temporal-windows.activities-enabled` | `false` | Aplica atividades/progresso/XP para ocorrencias temporais satisfeitas |

`DURACAO` abre janela quando todas as condicoes obrigatorias estao satisfeitas,
mantem evidencias enquanto continuam satisfeitas, invalida interrupcoes e expira
lacunas acima de `maximumSampleGap`. Ao atingir `fimPrevistoEm`, o worker
reconstroi os fatos do PostgreSQL e avalia com Drools.

`TRANSICAO` detecta mudanca real entre o valor anterior e atual do mesmo
sensor/parametro, para `NUMERIC`, `BOOLEAN` e `TEXT`, persistindo evidencias
`ANTES` e `DEPOIS`.

`JANELA_ENCERRADA` avalia o estado observado no fim de uma janela persistida,
usando os fatos/evidencias do intervalo. A semantica detalhada fica em
`Documentos/MissionEventWindowClosedSemantics.md`.

## Atividades E XP

As atividades sao instancias por pessoa, missao e `chave_ciclo`. A missao define
`ciclo_tipo`, `progresso_necessario` e `conclusao_automatica`; a atividade copia
essa configuracao ao ser criada.

`atividade_evento` registra vinculos idempotentes de progresso e conclusao entre
atividade e ocorrencia. Uma mesma ocorrencia nao adiciona progresso duas vezes.

Quando uma atividade e concluida automaticamente pela primeira vez por uma
ocorrencia confirmada, `XpRewardService` cria uma concessao append-only em
`xp_lancamento` com chave `xp:activity:{atividadeId}:completion`. Valor zero de
`Missao.value` nao cria lancamento. Conclusao manual por endpoint de atividade
nao concede XP automaticamente.

Consulta:

```text
GET /api/pessoas/{pessoaId}/xp
GET /api/pessoas/{pessoaId}/xp/lancamentos
```

`ADMIN` e `OPERADOR` consultam qualquer pessoa; usuario comum consulta apenas o
proprio saldo e extrato.

## Administracao Do Motor

Endpoints operacionais:

```text
GET  /api/admin/missions/events
GET  /api/admin/missions/evaluation-requests
GET  /api/admin/missions/windows
GET  /api/admin/missions/windows/{windowId}
GET  /api/admin/missions/windows/{windowId}/evidences
POST /api/admin/missions/windows/{windowId}/retry
POST /api/admin/missions/windows/{windowId}/satisfy
POST /api/admin/missions/windows/{windowId}/invalidate
POST /api/admin/missions/windows/{windowId}/expire
POST /api/admin/missions/windows/{windowId}/fail
GET  /api/admin/missions/occurrences
GET  /api/admin/missions/occurrences/{occurrenceId}
GET  /api/admin/missions/occurrences/{occurrenceId}/evidences
POST /api/admin/missions/occurrences/{occurrenceId}/status
GET  /api/admin/missions/workers/status
POST /api/admin/missions/workers/evaluation/run
POST /api/admin/missions/workers/temporal-windows/run
```

Essas rotas reutilizam os servicos existentes e servem para consulta,
diagnostico, transicoes operacionais controladas e execucao manual de workers.

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
| `PROCEL_MISSIONS_RULE_ENGINE` | Engine de regras; default `simple` |
| `PROCEL_MISSIONS_EVALUATION_WORKER_ENABLED` | Habilita worker de requests |
| `PROCEL_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ENABLED` | Habilita janelas temporais |
| `PROCEL_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_WORKER_ENABLED` | Habilita worker temporal |
| `PROCEL_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_DROOLS_ENABLED` | Habilita Drools temporal |
| `PROCEL_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ACTIVITIES_ENABLED` | Habilita atividades/XP a partir de ocorrencias temporais |

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

Os testes cobrem ingestao, idempotencia, seguranca, migrations, integracoes, rotas internas, jobs de agregacao, claim concorrente, lease, retry, buckets numericos, motor de missoes, Drools opt-in, janelas temporais, atividades, XP e administracao operacional com PostgreSQL via Testcontainers.
