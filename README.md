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
  ScriptsTestAPI/           # Script E2E PowerShell
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
- PowerShell, se for usar o script E2E

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
http://b8xxcv7fawgq3494xzbz97fw.187.77.58.122.sslip.io/
```

Documentacao Swagger nesse ambiente:

```text
http://b8xxcv7fawgq3494xzbz97fw.187.77.58.122.sslip.io/docs
```

Use esse host como `baseUrl` no Insomnia, Postman ou no script E2E quando quiser testar contra o servidor remoto.

## Documentacao Da API

Com a API em execucao:

```text
http://localhost:8080/docs
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

Usuario bootstrap para desenvolvimento:

```text
email:    admin@procel.local
password: admin123
```

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

## Teste E2E

Script PowerShell:

```powershell
.\Documentos\ScriptsTestAPI\OnStart.ps1
```

O script executa:

- login JWT;
- sync de salas;
- seed de sensores;
- criacao/busca/atualizacao de pessoa;
- check-in;
- ingestao mockada;
- consultas de medicoes;
- ocupacao/presencas abertas;
- checkout.

Antes de executar, confira no script:

```powershell
$BaseUrl
$RoomId
$SensorExternalId
$PessoaId
$PessoaEmail
$AdminEmail
$AdminPassword
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

Ambos documentam o fluxo E2E com login JWT e `Authorization: Bearer {{jwtToken}}`.

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
