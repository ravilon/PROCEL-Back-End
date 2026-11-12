# Proben-API

`Proben-API` e a API principal do PROBEN. Ela mantem o dominio canonico no PostgreSQL, autentica usuarios, recebe medicoes, administra sensores, perfis de integracao e executa jobs analiticos por janela.

A aplicacao e publicavel de forma independente. Ela depende somente de
PostgreSQL acessivel por `SPRING_DATASOURCE_URL` e nao depende de Admin,
Telemetry, MongoDB, MQTT ou do Compose integrado.

## Responsabilidades

- Autenticacao JWT de usuarios e servicos.
- Cadastro e consulta de pessoas, cursos, disciplinas, periodos, presencas, sensores e regras.
- Ingestao canonica idempotente de medicoes.
- Ingestao por perfis de integracao, parsers, versoes ativas e bindings.
- Rotas internas exclusivas para eventos vindos do `Proben-Telemetry`.
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
- `V26`-`V28`: catalogo oficial de missoes e contratos de estado/expiracao de eventos.
- `V29`-`V32`: observacoes externas compartilhadas, localizacao e coletores HTTP com credenciais gerenciadas.
- `V33`: regras ambientais.

`spring.jpa.hibernate.ddl-auto=validate`; Flyway e a fonte de verdade do schema.

## Contexto Academico

## Organizacao e JavaDoc

Os pacotes de casos de uso principais documentam seus limites e invariantes em `package-info.java`. Gere a documentacao Java local com `./mvnw javadoc:javadoc`; a saida fica em `target/site/apidocs`. Novos workers e integracoes devem documentar em JavaDoc suas pre-condicoes, efeitos colaterais, politica de retry e garantias de idempotencia/concorrencia.

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
`proben.missions.evaluation.temporal-windows.drools-enabled=true`.

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
| `proben.missions.rule-engine` | `simple` | Mantem `SimpleMissionRuleEngine` como padrao; `drools` e opt-in |
| `proben.missions.evaluation.worker-enabled` | `false` | Habilita worker de requests instantaneos |
| `proben.missions.evaluation.temporal-windows.enabled` | `false` | Habilita criacao/atualizacao de janelas temporais |
| `proben.missions.evaluation.temporal-windows.worker-enabled` | `false` | Habilita worker temporal |
| `proben.missions.evaluation.temporal-windows.drools-enabled` | `false` | Usa Drools no fluxo temporal |
| `proben.missions.evaluation.temporal-windows.activities-enabled` | `false` | Aplica atividades/progresso/XP para ocorrencias temporais satisfeitas |

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

## Contextos compartilhados e temperatura externa

`/api/shared-contexts` administra cobertura, localizacao, vinculos de salas,
selecao explicita de fonte e integracoes HTTP. Sensores compartilhados usam o
mesmo pipeline canonico, mas pertencem ao contexto (nunca a uma sala ficticia).
O coletor fica desabilitado por padrao e nao e chamado por consultas ou pelo
motor de regras.

Exemplo resumido para um contexto com duas salas:

```http
POST /api/shared-contexts
{"nome":"Campus Norte","latitude":-31.77,"longitude":-52.34,"timezone":"America/Sao_Paulo"}

PUT /api/shared-contexts/{contextId}/rooms
{"compartimentoIds":["SALA-101","SALA-102"]}

POST /api/sensor-admin/sensors
{"externalId":"weather-campus-norte","nome":"Estacao externa","tipoNome":"clima","contextoCompartilhadoId":"{contextId}"}

PUT /api/shared-contexts/{contextId}/integration
{"sensorExternalId":"weather-campus-norte","nome":"Provedor contratado","endpointUrl":"http://weather-mock/now","metodo":"GET","credential":"CHAVE_INFORMADA_PELA_INTERFACE","credentialHeader":"X-API-Key","temperatureJsonPointer":"/temperature","observedAtJsonPointer":"/observedAt","sourceIdJsonPointer":"/stationId","intervalSeconds":900,"timeoutMillis":5000,"maxAttempts":2}

PUT /api/shared-contexts/rooms/SALA-101/source
{"parametroDefId":"{temperaturaExternaParametroId}","sensorExternalId":"weather-campus-norte","validadeSegundos":3600}
```

Repita apenas a ultima selecao para `SALA-102`. O tipo `clima` deve possuir o
parametro numerico ativo `temperatura_externa` com unidade `°C`. Informe a
credencial pela interface de Integracoes e habilite
`PROBEN_SHARED_CONTEXT_COLLECTOR_ENABLED=true`. O provedor deve devolver um
horario ISO-8601 de observacao; ele e parte da identidade idempotente.

`GET .../latest` distingue `AVAILABLE`, `STALE` e `MISSING`. A media de sete
dias usa janela movel `[agora-7d, agora]`, no fuso cadastrado para exibicao,
media degrau ponderada pelo tempo, deduplicacao por timestamp, lacunas limitadas
a uma hora e cobertura minima de 120 horas. Sem cobertura, retorna
`INSUFFICIENT_COVERAGE` e nao fabrica valor.

### WeatherAPI e localizacao por campus

Campi podem receber latitude/longitude por `PUT /api/campuses/{nome}`. A
sincronizacao do Cobalto apenas localiza ou cria o campus e, portanto, nao
sobrescreve essas coordenadas complementares. Um contexto usa coordenadas
proprias (`locationMode=OWN`) ou referencia dinamicamente um campus
(`locationMode=CAMPUS`); a referencia geografica nao altera seus vinculos de
salas.

Preset WeatherAPI usado pela interface de Integracoes:

```json
{
  "endpointUrl": "https://api.weatherapi.com/v1/current.json",
  "metodo": "GET",
  "authMode": "QUERY",
  "credential": "CHAVE_INFORMADA_PELA_INTERFACE",
  "credentialQueryParam": "key",
  "coordinateQueryParam": "q",
  "temperatureJsonPointer": "/current/temp_c",
  "observedAtJsonPointer": "/current/last_updated_epoch",
  "observedAtFormat": "UNIX_SECONDS"
}
```

O backend monta `q=latitude,longitude` e injeta `key` a partir do segredo; a
chave nao aparece em DTOs, previews, auditoria ou logs. Cada execucao registra
coordenadas, origem da localizacao, campus, resultado e hash geografico. A media
de sete dias usa apenas observacoes do hash atualmente resolvido, evitando
mistura silenciosa depois de uma mudanca de localizacao.

Rotas principais:

```text
POST /api/sensors/ingest
POST /api/sensors/ingest/mock
GET  /api/sensors/{sensorExternalId}/medicoes
GET  /api/sensors/{sensorExternalId}/medicoes/latest
GET  /api/rooms/{compartimentoId}/medicoes
GET  /api/rooms/{compartimentoId}/medicoes/latest
```

O DTO canonico contem `messageId`, `sensorExternalId`, `timestamp`, `source`, `sourceReceivedAt` e `values`. A metadata de ingestao preserva contexto, idempotencia, produtor, timestamps e vinculo com eventos brutos quando a origem e o `Proben-Telemetry`.

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
| `PROBEN_JWT_SECRET` | Segredo JWT |
| `PROBEN_TELEMETRY_SERVICE_JWT_SECRET` | Alias aceito para o segredo usado por JWT de servico quando `PROBEN_JWT_SECRET` nao for definido |
| `PROBEN_JWT_EXPIRATION_MINUTES` | TTL JWT de usuario |
| `PROBEN_BOOTSTRAP_ADMIN_EMAIL` | Admin inicial |
| `PROBEN_BOOTSTRAP_ADMIN_PASSWORD` | Senha do admin inicial |
| `PROBEN_CORS_ALLOWED_ORIGIN_PATTERNS` | CORS |
| `PROBEN_ANALYTICS_AGGREGATION_WORKER_ENABLED` | Habilita worker analitico |
| `PROBEN_ANALYTICS_AGGREGATION_POLL_INTERVAL` | Intervalo de polling |
| `PROBEN_ANALYTICS_AGGREGATION_LEASE_TIMEOUT` | Timeout do lease |
| `PROBEN_ANALYTICS_AGGREGATION_MAX_ATTEMPTS` | Tentativas por janela |
| `PROBEN_ANALYTICS_AGGREGATION_BACKOFF` | Backoff por tentativa |
| `PROBEN_ANALYTICS_AGGREGATION_BATCH_SIZE` | Janelas por ciclo |
| `PROBEN_ANALYTICS_AGGREGATION_VERSION` | Versao dos buckets |
| `PROBEN_MISSIONS_RULE_ENGINE` | Engine de regras; default `simple` |
| `PROBEN_MISSIONS_EVALUATION_WORKER_ENABLED` | Habilita worker de requests |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ENABLED` | Habilita janelas temporais |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_WORKER_ENABLED` | Habilita worker temporal |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_DROOLS_ENABLED` | Habilita Drools temporal |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ACTIVITIES_ENABLED` | Habilita atividades/XP a partir de ocorrencias temporais |

## Execucao

```bash
docker compose -f compose.yaml up -d
./mvnw test
./mvnw spring-boot:run
```

`compose.yaml` sobe `Proben-API + PostgreSQL` para desenvolvimento isolado do
modulo. Para usar apenas PostgreSQL local, suba somente o servico `postgres`.

Build independente:

```bash
docker build -t proben-api .
```

Healthcheck:

```text
/actuator/health
```

No Coolify:

```text
Base directory: /Proben-API
Dockerfile: /Dockerfile
Healthcheck: /actuator/health
```

Configure `SPRING_DATASOURCE_URL`, usuario, senha, `PROBEN_JWT_SECRET` e CORS
com valores da plataforma. O PostgreSQL pode estar em outro container, servico
gerenciado ou outro host.

Swagger:

```text
http://localhost:8080/docs
http://localhost:8080/v3/api-docs
```

## Testes

Os testes cobrem ingestao, idempotencia, seguranca, migrations, integracoes, rotas internas, jobs de agregacao, claim concorrente, lease, retry, buckets numericos, motor de missoes, Drools opt-in, janelas temporais, atividades, XP e administracao operacional com PostgreSQL via Testcontainers.

### Credenciais gerenciadas das coletas HTTP

Credenciais informadas em Integrações são cifradas com AES-256-GCM, usando IV aleatório por gravação. A API precisa receber exclusivamente pelo ambiente uma chave mestra Base64 de 32 bytes:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Defina o resultado em `PROBEN_CREDENTIAL_ENCRYPTION_KEY` no ambiente seguro da API. Não grave esse valor em arquivos do projeto, banco ou frontend. Uma variável ausente bloqueia a criação, substituição e uso de credenciais. Uma variável presente com Base64 inválido ou tamanho diferente de 32 bytes impede a inicialização da API. A chave do provedor é informada exclusivamente pelo campo de senha da interface de Integrações.
