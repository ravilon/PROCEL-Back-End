# PROCEL Back-End

Repositorio dos artefatos de backend do PROCEL: API de ingestao analitica, documentacao de API, scripts de teste, DER e DDL do banco.

## Estrutura

```text
API-Doc/
  Insomnia/                 # Workspace exportado do Insomnia
  Postman/                  # Collections/environments Postman
Database/
  PROCEL-API/               # DDL versionado do banco analitico
Documentos/
  DER-BancoAnalitico/       # DER draw.io
  ApiSmokeTests/            # Script PowerShell de smoke test da API
Procel-Admin/               # Console web React/TypeScript
Procel-API/                 # API Spring Boot principal
```

## Procel-Admin

Console web independente para operacao e gerenciamento, construido com React,
TypeScript, Vite e Material UI. A imagem Docker usa Nginx e recebe a URL da API
pela variavel `API_BASE_URL`.

Organizacao principal do front-end:

```text
src/api/       # Clients HTTP por dominio
src/features/  # Fluxos/telas organizados por area funcional
src/pages/     # Entradas de rota
src/types/     # Contratos TypeScript por dominio
```

No Coolify, configure como uma aplicacao separada:

```text
Base directory: /Procel-Admin
Port: 80
Health check: /healthz
Watch paths: /Procel-Admin/**
```

Isso evita que alteracoes exclusivas do console reiniciem o backend e
interrompam jobs de ingestao.

## Procel-API

API Spring Boot responsavel por:

- sincronizar salas/compartimentos a partir do Cobalto ou arquivo local;
- carregar seed de sensores;
- cadastrar e autenticar pessoas;
- registrar check-in/checkout de presencas;
- cadastrar modelos de missoes e acompanhar atividades;
- gerar ingestao mockada de medicoes de sensores;
- consultar medicoes por sensor ou compartimento;
- cadastrar grupos/regras de qualificacao de parametros por sensor;
- avaliar medicoes contra regras DER/Parameter Qualification;
- expor documentacao Swagger/OpenAPI.

Stack principal:

- Java 21
- Spring Boot 4.0.3
- Spring Web
- Spring Data JPA
- Spring Security
- JWT stateless
- PostgreSQL
- Flyway
- springdoc-openapi/Swagger UI

## Como Rodar Localmente

Pre-requisitos:

- Java 21
- Docker ou PostgreSQL local
- PowerShell, se for usar o script de smoke test

Suba o PostgreSQL local:

```powershell
cd Procel-API
docker compose up -d
```

Execute a API:

```powershell
.\mvnw.cmd spring-boot:run
```

Por padrao, a aplicacao usa:

```text
PostgreSQL: localhost:5432
Database:   procel_analytics
User:       postgres
Password:   postgres
```

Em deploy, sobrescreva esses valores por variaveis de ambiente. Em bancos gerenciados
que exigem TLS/SSL, como costuma ocorrer em hospedagens externas, inclua
`sslmode=require` na URL JDBC:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:PORTA/NOME_DO_BANCO?sslmode=require
SPRING_DATASOURCE_USERNAME=USUARIO_DO_BANCO
SPRING_DATASOURCE_PASSWORD=SENHA_DO_BANCO
```

Falhas fatais de autenticacao/permissao no PostgreSQL encerram a API imediatamente,
em vez de manter tentativas indefinidas de conexao quando a senha, usuario, host ou
permissao do banco estiverem incorretos.

Os tempos de tentativa podem ser ajustados por variaveis de ambiente:

```text
SPRING_DATASOURCE_CONNECTION_TIMEOUT_MS=10000
SPRING_DATASOURCE_VALIDATION_TIMEOUT_MS=5000
SPRING_DATASOURCE_INITIALIZATION_FAIL_TIMEOUT_MS=1
SPRING_DATASOURCE_LOGIN_TIMEOUT_SECONDS=10
```

O banco local usa o volume Docker `postgres_data`, entao os dados persistem entre reinicios normais. Para manter os dados, pare os containers com:

```powershell
docker compose down
```

Nao use `docker compose down -v` se quiser preservar o banco, porque `-v` remove o volume do PostgreSQL.

O schema e seeds versionados sao aplicados automaticamente pelo Flyway:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
    encoding: UTF-8
```

As migrations ficam em:

```text
Procel-API/src/main/resources/db/migration/
```

`V2__mission_catalog_and_activity_expiration.sql` registra as 30 missoes padrao como tipo `Individual`, com `value` numerico de XP, e atualiza o status `EXPIRADA` para atividades. `V3__rename_legacy_activity_table.sql` renomeia bancos existentes para a tabela canonica `atividade`. `V4__mission_type_individual_seed.sql` garante `missao.tipo = Individual` em bancos que ja tinham recebido o seed antes desse campo. `V5__mission_seed_xp_values.sql` garante os valores de XP das missoes seed em bancos existentes.

O Hibernate ainda esta configurado para atualizar ajustes de schema durante desenvolvimento:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

## CORS E Front-End

A API habilita CORS para permitir chamadas feitas por navegadores a partir de outro origin, por exemplo um front-end rodando no computador de um desenvolvedor enquanto a API esta publicada em um servidor.

Configuracao principal:

```yaml
procel:
  cors:
    allowed-origin-patterns: ${PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS:http://localhost:*,http://127.0.0.1:*,http://192.168.*.*:*,http://10.*.*.*:*,http://172.16.*.*:*,http://172.17.*.*:*,http://172.18.*.*:*,http://172.19.*.*:*,http://172.20.*.*:*,http://172.21.*.*:*,http://172.22.*.*:*,http://172.23.*.*:*,http://172.24.*.*:*,http://172.25.*.*:*,http://172.26.*.*:*,http://172.27.*.*:*,http://172.28.*.*:*,http://172.29.*.*:*,http://172.30.*.*:*,http://172.31.*.*:*}
```

Com essa configuracao, um front-end local como `http://localhost:5173` ou `http://127.0.0.1:3000` pode chamar uma API publicada, por exemplo `https://seu-dominio.com/api/...`.

Para producao, restrinja a variavel ao dominio real do front-end:

```powershell
$env:PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS = "https://seu-front-end.com"
```

Se o front-end de desenvolvimento usar HTTPS local, adicione tambem:

```powershell
$env:PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS = "http://localhost:*,http://127.0.0.1:*,https://localhost:*,https://127.0.0.1:*"
```

No front-end, use sempre a URL completa da API publicada. `localhost` no navegador do usuario aponta para o proprio computador do usuario, nao para o servidor.

## Servidor De Testes

Tambem existe uma instancia publicada via Coolify para testes sem rodar o backend localmente:

```text
https://procel.servehttp.com
```

Documentacao Swagger nesse ambiente:

```text
https://procel.servehttp.com/docs
https://procel.servehttp.com/swagger-ui/index.html
```

Use esse host como `baseUrl` no Insomnia, Postman ou no script de smoke test quando quiser testar contra o servidor remoto.

## Documentacao Da API

Com a API em execucao:

```text
http://localhost:8080/docs
http://localhost:8080/swagger-ui/index.html
```

OpenAPI bruto:

```text
http://localhost:8080/v3/api-docs
http://localhost:8080/v3/api-docs.yaml
```

O Swagger possui suporte a Bearer JWT. Faca login em `POST /api/auth/login`, copie o `accessToken`, clique em `Authorize` e informe o token.

## Autenticacao E Acesso

A API usa JWT stateless via header:

```http
Authorization: Bearer <token>
```

Roles disponiveis:

```text
ADMIN
OPERADOR
ANALISTA
USUARIO
INGESTOR
```

Usuarios para desenvolvimento/testes:

```text
Admin bootstrap:
  email:    admin@procel.local
  password: admin123
  role:     ADMIN

Usuario criado pelo smoke test:
  userId:   api-test-user
  email:    api-test-user@procel.local
  password: 123456
  role:     USUARIO
```

Essas credenciais existem para facilitar testes locais e no ambiente remoto de homologacao. Nao use esses valores como credenciais reais de producao.

Variaveis relevantes:

```text
PROCEL_JWT_SECRET
PROCEL_JWT_EXPIRATION_MINUTES
PROCEL_BOOTSTRAP_ADMIN_ENABLED
PROCEL_BOOTSTRAP_ADMIN_USER_ID
PROCEL_BOOTSTRAP_ADMIN_NOME
PROCEL_BOOTSTRAP_ADMIN_EMAIL
PROCEL_BOOTSTRAP_ADMIN_PASSWORD
```

Em producao, configure um `PROCEL_JWT_SECRET` forte e altere/desabilite o usuario bootstrap:

```text
PROCEL_BOOTSTRAP_ADMIN_ENABLED=false
```

## Fluxo De Usuario

Auto cadastro publico:

```http
POST /api/auth/register
```

Cria sempre uma pessoa com role:

```text
USUARIO
```

Login:

```http
POST /api/auth/login
```

Gestao administrativa de pessoas:

```http
POST   /api/pessoas
GET    /api/pessoas/{id}
PUT    /api/pessoas/{id}
DELETE /api/pessoas/{id}
```

Regras:

- `POST /api/auth/register` e publico e nao aceita escolha de role.
- `POST /api/pessoas` exige `ADMIN` e permite criar usuarios com roles especificas.
- `PUT /api/pessoas/{id}` permite que o proprio usuario atualize seus dados, mas roles enviadas por usuario comum sao ignoradas.
- Apenas `ADMIN` altera roles.

## Endpoints Principais

Autenticacao:

```text
POST /api/auth/register
POST /api/auth/login
```

Pessoas:

```text
POST   /api/pessoas
GET    /api/pessoas/{id}
PUT    /api/pessoas/{id}
DELETE /api/pessoas/{id}
```

Presencas:

```text
POST /api/presencas/checkin
POST /api/presencas/checkout
POST /api/presencas/checkout/by-pessoa
GET  /api/presencas/ocupacao/compartimentos/{compartimentoId}
GET  /api/presencas/abertas/compartimentos/{compartimentoId}
```

Missoes:

```text
POST   /api/missoes
GET    /api/missoes
GET    /api/missoes?ativo=true
GET    /api/missoes/{missaoId}
PUT    /api/missoes/{missaoId}
DELETE /api/missoes/{missaoId}
```

`DELETE /api/missoes/{missaoId}` nao remove a linha. Ele depreca a missao, marcando `ativo=false`, e expira atividades abertas dessa missao.

Modelos de missao possuem `tipo` e `value`. O seed padrao usa `tipo = Individual` e grava em `value` apenas o numero de XP recomendado.

Atividades:

```text
POST   /api/pessoas/{pessoaId}/atividades
GET    /api/pessoas/{pessoaId}/atividades
GET    /api/pessoas/{pessoaId}/atividades?status={status}
GET    /api/pessoas/{pessoaId}/atividades/resumo
GET    /api/pessoas/{pessoaId}/atividades/{atividadeId}
PUT    /api/pessoas/{pessoaId}/atividades/{atividadeId}
DELETE /api/pessoas/{pessoaId}/atividades/{atividadeId}
```

Status suportados para atividades:

```text
PENDENTE, EM_ANDAMENTO, CONCLUIDA, EXPIRADA, CANCELADA
```

`missao` e o modelo/catalogo. Uma missao pode informar `parentId` para formar uma arvore de objetivos e etapas filhas; ciclos e autorreferencia sao rejeitados. Na atualizacao, `parentId: null` move a missao para a raiz. Ao atribuir uma missao pai, a API cria atividades para toda a subarvore ativa. O status e o progresso da atividade pai sao derivados das filhas: quando todas as filhas diretas estao `CONCLUIDA`, a pai e concluida automaticamente. As respostas de atividade incluem `missaoParentId`, `totalFilhas`, `filhasConcluidas` e `progressoPercentual`. Atividades nao sao apagadas; o delete logico marca `EXPIRADA`.

Salas e sensores:

```text
POST /api/rooms/sync
POST /api/sensors/seed/from-resource
POST /api/sensors/ingest
POST /api/sensors/ingest/integrations/{profileId}
POST /api/sensors/{sensorExternalId}/ingest/integrations/{profileId}
POST /api/sensors/ingest/mock
GET  /api/sensor-integrations/snapshot
GET  /api/sensor-admin/types
POST /api/sensor-admin/types
POST /api/sensor-admin/types/{tipoNome}/parameters
POST /api/sensor-admin/sensors
```

### Contrato Canonico De Ingestao

Endpoint:

```http
POST /api/sensors/ingest
Authorization: Bearer <token>
Content-Type: application/json
```

O token precisa pertencer a um usuario com role `ADMIN` ou `INGESTOR`. O `producerId`
usado para idempotencia nao vem no corpo: ele e derivado de `authentication.getName()`,
ou seja, do usuario autenticado no JWT. Produtores diferentes podem reutilizar o mesmo
`messageId` sem colisao entre si.

Request:

```json
{
  "messageId": "sii-smart-001-2026-08-11T23:30:00Z",
  "sensorExternalId": "SII-001",
  "timestamp": "2026-08-11T23:30:00Z",
  "source": "MQTT",
  "sourceReceivedAt": "2026-08-11T23:30:02Z",
  "values": {
    "temperature_c": 23.7,
    "presence": true,
    "label": "ok"
  }
}
```

Campos:

- `messageId`: obrigatorio, texto nao vazio. Deve identificar uma mensagem de forma estavel dentro do produtor e do sensor.
- `sensorExternalId`: obrigatorio, texto nao vazio. Deve apontar para um sensor ativo cadastrado.
- `timestamp`: obrigatorio, ISO-8601. Representa o instante medido pelo sensor.
- `source`: obrigatorio. Valores aceitos: `MQTT`, `REST`, `FILE`, `API`.
- `sourceReceivedAt`: opcional, ISO-8601. Representa quando o produtor recebeu a mensagem da origem.
- `values`: obrigatorio, objeto nao vazio. As chaves devem ser nomes de `ParametroDef` ativos para o tipo do sensor.

Idempotencia:

- A chave idempotente e `(producerId, sensorExternalId, messageId)`.
- Se a primeira chamada for aceita, a API persiste a medicao e metadados de ingestao.
- Se a mesma chave chegar novamente com payload canonico equivalente, a API retorna a medicao existente.
- Se a mesma chave chegar com payload canonico divergente, a API retorna conflito.
- A equivalencia considera `messageId`, `sensorExternalId`, `source`, `timestamp` e `values`; `sourceReceivedAt` nao participa do fingerprint.
- Em `values`, a ordem das chaves nao altera o fingerprint. Numeros sao normalizados para evitar conflito entre representacoes equivalentes como `23.70` e `23.7`.

Resposta `201 Created`:

```json
{
  "status": "CREATED",
  "code": "MEASUREMENT_INGESTED",
  "duplicate": false,
  "medicaoId": "cfd0d5e4-8ca4-45c8-9479-2e6108fc0d32",
  "messageId": "sii-smart-001-2026-08-11T23:30:00Z",
  "apiReceivedAt": "2026-08-11T23:30:03.123Z",
  "originalApiReceivedAt": null,
  "duplicateDetectedAt": null,
  "conflictDetectedAt": null,
  "message": null
}
```

Resposta `200 OK` para duplicata equivalente:

```json
{
  "status": "DUPLICATE",
  "code": "DUPLICATE_MESSAGE",
  "duplicate": true,
  "medicaoId": "cfd0d5e4-8ca4-45c8-9479-2e6108fc0d32",
  "messageId": "sii-smart-001-2026-08-11T23:30:00Z",
  "apiReceivedAt": null,
  "originalApiReceivedAt": "2026-08-11T23:30:03.123Z",
  "duplicateDetectedAt": "2026-08-11T23:31:10.456Z",
  "conflictDetectedAt": null,
  "message": null
}
```

Resposta `409 Conflict` para mesma chave com payload divergente:

```json
{
  "status": "CONFLICT",
  "code": "IDEMPOTENCY_CONFLICT",
  "duplicate": true,
  "medicaoId": "cfd0d5e4-8ca4-45c8-9479-2e6108fc0d32",
  "messageId": "sii-smart-001-2026-08-11T23:30:00Z",
  "apiReceivedAt": null,
  "originalApiReceivedAt": "2026-08-11T23:30:03.123Z",
  "duplicateDetectedAt": null,
  "conflictDetectedAt": "2026-08-11T23:31:10.456Z",
  "message": "A different payload was already ingested for this producer, sensor and messageId."
}
```

Erros estruturados usam o formato:

```json
{
  "message": "Active sensor not found: SII-999",
  "error": "SENSOR_NOT_FOUND",
  "timestamp": "2026-08-11T23:30:03.123Z"
}
```

Codigos relevantes:

- `400 BAD_REQUEST`: campos obrigatorios ausentes/vazios, `values` vazio ou corpo invalido.
- `400 SOURCE_INVALID`: `source` fora de `MQTT`, `REST`, `FILE`, `API`.
- `400 TIMESTAMP_INVALID`: timestamp sem formato ISO-8601 valido.
- `401 UNAUTHORIZED`: token ausente ou invalido.
- `403 FORBIDDEN`: usuario autenticado sem role `ADMIN` ou `INGESTOR`.
- `404 SENSOR_NOT_FOUND`: `sensorExternalId` nao existe ou nao esta ativo.
- `409 IDEMPOTENCY_CONFLICT`: mesma chave idempotente com payload divergente.
- `422 PARAMETER_NOT_ACCEPTED`: chave em `values` nao aceita pelo tipo do sensor.
- `422 VALUE_TYPE_INVALID`: valor nao conversivel para o tipo do parametro.

### Perfis De Integracao E Parser

Perfis de integracao permitem receber payloads externos e transforma-los no contrato
canonico usando Jackson Tree e JSON Pointer. O escopo atual e REST dentro da
Procel-API; MQTT, cache externo, retry de transporte e Procel-Telemetry ficam fora
desta fatia.

Endpoints administrativos exigem `ADMIN`:

```text
GET    /api/sensor-integrations/profiles?includeInactive=false
POST   /api/sensor-integrations/profiles
GET    /api/sensor-integrations/profiles/{profileId}
PUT    /api/sensor-integrations/profiles/{profileId}
POST   /api/sensor-integrations/profiles/{profileId}/activate
DELETE /api/sensor-integrations/profiles/{profileId}
POST   /api/sensor-integrations/profiles/{profileId}/versions
GET    /api/sensor-integrations/profiles/{profileId}/versions
GET    /api/sensor-integrations/profiles/{profileId}/versions/{versionId}
PUT    /api/sensor-integrations/profiles/{profileId}/versions/{versionId}
POST   /api/sensor-integrations/profiles/{profileId}/versions/{versionId}/activate
POST   /api/sensor-integrations/profiles/{profileId}/bindings
GET    /api/sensor-integrations/profiles/{profileId}/bindings?includeInactive=false
POST   /api/sensor-integrations/bindings/{bindingId}/activate
DELETE /api/sensor-integrations/bindings/{bindingId}
```

Versoes de parser nascem como `DRAFT`. Apenas `DRAFT` pode ser editada. Ao ativar,
a versao vira `ACTIVE`, recebe `publishedAt` e passa a ser imutavel. Se ja existir
uma versao `ACTIVE` do mesmo perfil, ela passa para `INACTIVE` na mesma transacao.
A ativacao exige informar a versao ativa esperada para evitar atualizacao perdida:

```json
{
  "expectedActiveVersionId": null
}
```

Configuracao de parser:

```json
{
  "sensorResolutionMode": "PAYLOAD_POINTER",
  "messageIdPointer": "/meta/id",
  "sensorExternalIdPointer": "/device/id",
  "timestampPointer": "/measuredAt",
  "sourceReceivedAtPointer": "/receivedAt",
  "timestampFormat": "ISO_INSTANT",
  "valueMappings": [
    {
      "parameterName": "temperature_c",
      "valuePointer": "/readings/temperature",
      "required": true
    }
  ]
}
```

`sensorResolutionMode` aceita `PAYLOAD_POINTER` ou `ROUTE_SENSOR`. Em
`PAYLOAD_POINTER`, o sensor vem do payload. Em `ROUTE_SENSOR`, use:

```http
POST /api/sensors/{sensorExternalId}/ingest/integrations/{profileId}
```

Ingestao com sensor extraido do payload:

```http
POST /api/sensors/ingest/integrations/{profileId}
Authorization: Bearer <token>
Content-Type: application/json
```

Para toda ingestao por perfil, a API exige perfil ativo, versao `ACTIVE`, sensor
ativo e binding ativo entre perfil e sensor. `source` vem somente do perfil;
`producerId` vem do JWT; `sourceReceivedAt` pode vir somente do JSON Pointer da
versao e nao entra no fingerprint.

Limites padrao:

```text
procel.integrations.parser.max-payload-bytes=262144
procel.integrations.parser.max-mappings=100
procel.integrations.parser.max-depth=64
```

Payload acima do limite e rejeitado antes da materializacao do JSON com:

```json
{
  "error": "PAYLOAD_TOO_LARGE",
  "message": "Integration payload exceeds max size",
  "timestamp": "2026-08-11T23:30:03.123Z"
}
```

Snapshot para produtores autorizados (`ADMIN` ou `INGESTOR`):

```http
GET /api/sensor-integrations/snapshot
```

Retorna apenas perfis ativos, versoes `ACTIVE`, bindings ativos e sensores ativos,
sem segredos.

Medicoes:

```text
GET /api/sensors/{sensorExternalId}/medicoes/latest
GET /api/sensors/{sensorExternalId}/medicoes?from={fromIso}&to={toIso}&page=0&limit=50
GET /api/rooms/{compartimentoId}/medicoes/latest
GET /api/rooms/{compartimentoId}/medicoes?from={fromIso}&to={toIso}&page=0&limit=50
```

Datas `from` e `to` devem estar em ISO-8601, por exemplo:

```text
2026-03-04T05:00:00Z
```

`page` inicia em zero e `limit` aceita ate 1000 registros. As qualificacoes `IDEAL` e `NORMAL` podem ser apresentadas como aprovadas; `ALERTA`, `CRITICO` e `INVALIDO` como reprovadas. Ausencia de qualificacao significa que nenhuma regra ativa foi disparada para o parametro, nao uma aprovacao implicita.

### Agregacoes Analiticas

A API possui orquestracao administrativa para jobs assincronos de agregacao por
periodo:

```text
POST /api/analytics/aggregation-jobs
GET  /api/analytics/aggregation-jobs/{id}
```

A etapa atual persiste buckets numericos em `analytics_numeric_bucket`. Cada bucket
representa um intervalo semiaberto `[bucket_start, bucket_end)`, um sensor, um
`parametro_def` numerico e uma versao de agregacao. A identidade logica e:

```text
sensor_external_id + parametro_def_id + bucket_start + bucket_end + aggregation_version
```

As estatisticas gravadas sao media, minimo, maximo e quantidade de amostras,
considerando apenas `parametro_valor.numeric_value` nao nulo e parametros com
`data_type = NUMERIC`. Valores booleanos e textuais nao sao agregados nesta etapa.
A precisao gravada usa escala decimal de 6 casas, sem conversao para `double`.

Reexecucoes recalculam somente o bucket da janela exata em processamento, removendo
o resultado anterior daquele intervalo e reinserindo os grupos encontrados na mesma
transacao. Se uma recomputacao nao encontrar amostras, o bucket anterior da janela e
removido. O `compartimento_id` registrado vem do sensor no modelo relacional atual;
historico de movimentacao de sensores entre compartimentos fica fora desta etapa.

A consulta publica ampla de series analiticas e filtros de leitura dos buckets sera
implementada em etapa posterior.

Regras e qualificacao de parametros:

```text
GET  /api/rules/parameter-defs?tipoNome={tipoNome}
POST /api/rules/groups
GET  /api/rules/groups
POST /api/rules/groups/{grupoId}/rules
GET  /api/rules/groups/{grupoId}/rules
POST /api/rules/groups/{grupoId}/rooms
POST /api/rules/sensors/{sensorExternalId}/groups
GET  /api/rules/sensors/{sensorExternalId}/groups
```

Esses endpoints permitem criar grupos de regras, cadastrar regras por `parametro_def`, vincular grupos a sensores e ativar uma configuracao de qualificacao. Durante a ingestao, cada `parametro_valor` medido e avaliado contra os grupos ativos do sensor. Resultados sao persistidos em `avaliacao_parametro_valor` e retornados nas consultas de medicoes no campo `qualificacoes`.

Cardinalidade aplicada na API:

- Um `parametro_def` pode ter varias `regra_parametro` globalmente, porque regras diferentes podem existir para estudos, grupos ou sensores diferentes.
- Um `grupo_regra` nao pode ter duas regras ativas para o mesmo `parametro_def`.
- Um sensor so pode ter um vinculo ativo/agendado, com janela de validade sobreposta, que produza regra ativa para um mesmo `parametro_def`.
- Regras vinculadas a um sensor precisam pertencer ao mesmo `tipo_de_sensor` do sensor.
- O vinculo em lote por salas aplica o grupo somente aos sensores compativeis com o unico tipo de sensor usado pelas regras ativas do grupo.

Filtros de compartimentos:

```text
GET /api/catalog/compartimentos?q={texto}&tipo={tipo}&predio={predio}&unidade={unidade}&campus={campus}
```

Operadores suportados:

```text
NUMERIC: GT, GTE, LT, LTE, EQ, NEQ, BETWEEN, OUTSIDE
BOOLEAN: EQ, NEQ
TEXT:    EQ, NEQ, CONTAINS
```

Resultados possiveis:

```text
IDEAL
NORMAL
ALERTA
CRITICO
INVALIDO
```

## Smoke Test Da API

Script PowerShell:

```powershell
.\Documentos\ApiSmokeTests\ApiSmokeTest.ps1
```

Por padrao, o script usa o ambiente remoto:

```powershell
.\Documentos\ApiSmokeTests\ApiSmokeTest.ps1
```

Para executar contra a API local:

```powershell
.\Documentos\ApiSmokeTests\ApiSmokeTest.ps1 -Target local
```

Para informar uma URL manualmente:

```powershell
.\Documentos\ApiSmokeTests\ApiSmokeTest.ps1 -BaseUrlOverride "http://localhost:8080"
```

Tambem e possivel usar variaveis de ambiente:

```powershell
$env:PROCEL_API_TARGET = "local"
.\Documentos\ApiSmokeTests\ApiSmokeTest.ps1

$env:PROCEL_API_BASE_URL = "http://localhost:8081"
.\Documentos\ApiSmokeTests\ApiSmokeTest.ps1
```

O script executa:

- login JWT;
- sync de salas;
- seed de sensores;
- criacao/busca/atualizacao de pessoa;
- catalogo de missoes, atividades, resumo por status e expiracao/deprecacao logica;
- check-in;
- criacao de grupo/regra DER para `temperature_c`;
- vinculo do grupo de regra ao sensor `SII-001`;
- ingestao mockada;
- verificacao de `qualificacoes.temperature_c` na ultima medicao;
- consultas de medicoes;
- ocupacao/presencas abertas;
- checkout.

Antes de executar, confira no script:

```powershell
$RoomId
$SensorExternalId
$SensorTipoNome
$RuleParametroNome
$PessoaId
$PessoaEmail
$AdminEmail
$AdminPassword
```

Depois de executar o smoke test localmente, use as queries abaixo para estudar e validar os dados gravados no PostgreSQL:

```powershell
psql -h localhost -p 5432 -U postgres -d procel_analytics -f .\Documentos\ApiSmokeTests\VerifyParameterQualification.sql
```

Arquivo:

```text
Documentos/ApiSmokeTests/VerifyParameterQualification.sql
```

## Documentacao De API Para Clientes

Insomnia:

```text
API-Doc/Insomnia/Insomnia_2026-05-12.yaml
```

Postman:

```text
API-Doc/Postman/PROCEL-API/
```

Ambos documentam o fluxo de smoke test com login JWT, `Authorization: Bearer {{jwtToken}}`, catalogo e hierarquia de missoes via `parentId`, atividades com `CONCLUIDA`/`EXPIRADA`, administracao de tipos de sensor e parametros, DER/Parameter Qualification, ingestao mockada e consultas paginadas de medicoes com `qualificacoes`.

O Procel-Admin disponibiliza fluxos visuais para cadastro de cursos, sensores, tipos e parametros, composicao de grupos de regras, associacao em lote por salas, consulta de medicoes por periodo, administracao hierarquica de missoes, progresso de atividades, vinculacao de disciplinas e edicao de usuarios.

A interface inclui acesso direto ao Swagger da API em `{API_BASE_URL}/swagger-ui/index.html`. Versao administrativa atual: `0.1`, desenvolvida para o PROCEL. Contato: Ravilon Aguiar, 2026, `ravilonaguiardossantos@gmail.com`.

## Banco Analitico

DDL versionado:

```text
Database/PROCEL-API/createAnaliticalDB.sql
```

Esse arquivo deve ser mantido como referencia do schema analitico. A fonte automatica de evolucao do banco agora sao as migrations Flyway em `Procel-API/src/main/resources/db/migration`.

Historicamente, o DDL tambem podia ser gerado pelo Hibernate em:

```text
Procel-API/target/schema.sql
```

O arquivo `target/schema.sql`, quando existir, e artefato de build e nao deve ser tratado como fonte versionada.

Modelo atual inclui:

```text
campus
predio
unidade
compartimento
pessoa
pessoa_role
presenca
tipo_de_sensor
sensor
medicao
parametro_def
parametro_valor
missao
atividade
grupo_regra
regra_parametro
sensor_grupo_regra
avaliacao_parametro_valor
```

DER:

```text
Documentos/DER-BancoAnalitico/DER-Salas.drawio
```

## Configuracao Cobalto

Configuracoes principais em `Procel-API/src/main/resources/application.yml`:

```yaml
procel:
  rooms:
    source: cobalto # cobalto | resource
    resource-path: seed/cobalto-compartimentos.sample.json
  cobalto:
    rooms:
      url: https://cobalto.ufpel.edu.br/servicosgerais/consultas/salasDeAula/listaSalas/
      timeout-ms: 60000
      page-size: 1000
      php-sessid: ""
```

Se `source` for `resource`, a API usa o JSON local configurado em `resource-path`.

## Comandos Uteis

Compilar e testar o modulo principal:

```powershell
cd Procel-API
.\mvnw.cmd clean test
```

Gerar/atualizar `target/schema.sql` via build da aplicacao:

```powershell
.\mvnw.cmd test
```

Parar o PostgreSQL local:

```powershell
docker compose down
```
