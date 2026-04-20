# PROCEL Back-End

Repositorio dos artefatos de backend do PROCEL: API de ingestao analitica, documentacao de API, scripts de teste, DER e DDL do banco.

## Estrutura

```text
API-Doc/
  Insomnia/                 # Workspace exportado do Insomnia
  Postman/                  # Collections/environments Postman
Database/
  PROCEL-Ingestion/         # DDL versionado do banco analitico
Documentos/
  DER-BancoAnalitico/       # DER draw.io
  ApiSmokeTests/            # Script PowerShell de smoke test da API
Procel-Ingestion/           # API Spring Boot principal
```

## Procel-Ingestion

API Spring Boot responsavel por:

- sincronizar salas/compartimentos a partir do Cobalto ou arquivo local;
- carregar seed de sensores;
- cadastrar e autenticar pessoas;
- registrar check-in/checkout de presencas;
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
- springdoc-openapi/Swagger UI

## Como Rodar Localmente

Pre-requisitos:

- Java 21
- Docker ou PostgreSQL local
- PowerShell, se for usar o script de smoke test

Suba o PostgreSQL local:

```powershell
cd Procel-Ingestion
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

Salas e sensores:

```text
POST /api/rooms/sync
POST /api/sensors/seed/from-resource
POST /api/sensors/ingest/mock
```

Medicoes:

```text
GET /api/sensors/{sensorExternalId}/medicoes/latest
GET /api/sensors/{sensorExternalId}/medicoes?from={fromIso}&to={toIso}&limit=50
GET /api/rooms/{compartimentoId}/medicoes/latest
GET /api/rooms/{compartimentoId}/medicoes?from={fromIso}&to={toIso}&limit=50
```

Datas `from` e `to` devem estar em ISO-8601, por exemplo:

```text
2026-03-04T05:00:00Z
```

Regras e qualificacao de parametros:

```text
GET  /api/rules/parameter-defs?tipoNome={tipoNome}
POST /api/rules/groups
GET  /api/rules/groups
POST /api/rules/groups/{grupoId}/rules
GET  /api/rules/groups/{grupoId}/rules
POST /api/rules/sensors/{sensorExternalId}/groups
GET  /api/rules/sensors/{sensorExternalId}/groups
```

Esses endpoints permitem criar grupos de regras, cadastrar regras por `parametro_def`, vincular grupos a sensores e ativar uma configuracao de qualificacao. Durante a ingestao, cada `parametro_valor` medido e avaliado contra os grupos ativos do sensor. Resultados sao persistidos em `avaliacao_parametro_valor` e retornados nas consultas de medicoes no campo `qualificacoes`.

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
API-Doc/Insomnia/Insomnia_2026-04-18.yaml
```

Postman:

```text
API-Doc/Postman/PROCEL-Ingestion/
```

Ambos documentam o fluxo de smoke test com login JWT, `Authorization: Bearer {{jwtToken}}`, DER/Parameter Qualification, ingestao mockada e consultas de medicoes com `qualificacoes`.

## Banco Analitico

DDL versionado:

```text
Database/PROCEL-Ingestion/createAnaliticalDB.sql
```

Esse arquivo foi alinhado com o DDL gerado pelo Hibernate em:

```text
Procel-Ingestion/target/schema.sql
```

O arquivo `target/schema.sql` e gerado automaticamente e nao deve ser tratado como fonte versionada.

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

Configuracoes principais em `Procel-Ingestion/src/main/resources/application.yml`:

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
cd Procel-Ingestion
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
