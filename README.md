# PROCEL

PROCEL e uma plataforma para receber telemetria bruta, transformar eventos em medicoes canonicas, administrar sensores e perfis de integracao, e preparar processamento analitico por periodos.

O repositorio contem tres aplicacoes principais:

| Aplicacao | Responsabilidade | Banco |
| --- | --- | --- |
| `Procel-API` | API principal, autenticacao, dominio academico, sensores, medicoes canonicas, perfis de integracao e jobs analiticos | PostgreSQL |
| `Procel-Telemetry` | Recebimento bruto REST/MQTT, idempotencia bruta, armazenamento no MongoDB, worker canonico para envio ao `Procel-API` e operacao administrativa da telemetria | MongoDB |
| `Procel-Admin` | Console web administrativo para catalogo, sensores, regras, integracoes, sincronizacoes, analises e operacao da telemetria | N/A |

## Estrutura

```text
.
|-- Procel-API/          # Spring Boot, PostgreSQL, Flyway, dominio canonico
|-- Procel-Telemetry/    # Spring Boot, MongoDB, MQTT 5, worker canonico
|-- Procel-Admin/        # React/Vite, console administrativo
|-- API-Doc/             # Postman, Insomnia e orientacao dos contratos
|-- Database/            # SQL legado e consultas auxiliares
|-- Documentos/          # Arquitetura, DER, MongoDB, MQTT, seguranca e catalogo de dados
`-- README.md
```

## Tecnologias

| Area | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 4.0.3, Spring Security, Spring Data JPA/MongoDB |
| Banco canonico | PostgreSQL, Flyway |
| Banco bruto | MongoDB |
| MQTT | MQTT 5, Eclipse Paho MQTT v5, QoS 1 |
| Frontend | React 19, TypeScript, Vite 7, MUI 7, MUI X Charts, React Query |
| Testes | JUnit 5, Testcontainers, Vitest, Testing Library |
| Documentacao API | springdoc OpenAPI, Postman 2.1, Insomnia YAML |

## Arquitetura Atual

Dispositivos e produtores podem enviar telemetria bruta por REST ou MQTT para o `Procel-Telemetry`. O evento bruto e persistido em `raw_telemetry_events` no MongoDB, com payload original, hash, identidade do produtor, `messageId`, timestamps e estado de processamento.

O worker canonico do `Procel-Telemetry`, desabilitado por padrao, faz claim atomico de eventos `RECEIVED`, consulta o snapshot de integracao do `Procel-API`, seleciona o perfil/parser sem resolver ambiguidades silenciosamente e chama a rota interna de ingestao do `Procel-API` com JWT curto de role fixa `TELEMETRY_SERVICE`.

O `Procel-API` persiste medicoes canonicas no PostgreSQL, com metadata de ingestao, perfis de integracao, versoes de parser, bindings, jobs de agregacao e buckets numericos. O `Procel-Admin` consome `Procel-API` e `Procel-Telemetry` usando o JWT do usuario autenticado.

`Procel-Telemetry` nao acessa diretamente o PostgreSQL.

## Fluxos

### Ingestao REST Canonica

Clientes autorizados como `ADMIN` ou `INGESTOR` podem enviar medicoes canonicas diretamente ao `Procel-API`:

```text
POST /api/sensors/ingest
POST /api/sensors/ingest/mock
```

A idempotencia canonica usa metadata de ingestao no PostgreSQL. Duplicatas equivalentes retornam sucesso idempotente; conflitos retornam erro sem sobrescrever a medicao original.

### Integracao por Perfil

Perfis de integracao definem o modo de rota (`ROUTE_SENSOR` ou `PAYLOAD_POINTER`), versoes de parser e bindings ativos. As rotas publicas de ingestao por perfil permanecem separadas das rotas internas usadas pela telemetria:

```text
POST /api/sensors/ingest/integrations/{profileId}
POST /api/sensors/{sensorExternalId}/ingest/integrations/{profileId}
```

### Entrada MQTT

O `Procel-Telemetry` assina, quando habilitado:

```text
procel/telemetry/v1/+/+/events
procel/telemetry/v1/+/events
```

O `producerId` vem exclusivamente do topico. O `sensorId` do topico prevalece sobre o envelope; divergencia entre topico e envelope e descartada com ACK. O `messageId` e obrigatorio no envelope JSON e nao usa o packet identifier MQTT. O ACK manual ocorre somente depois de persistencia, duplicata equivalente, conflito idempotente ou descarte permanente. Falha transitoria do MongoDB nao recebe ACK para permitir redelivery.

### Worker Telemetry -> Procel-API

O worker canonico:

- faz claim atomico `RECEIVED -> PROCESSING` via MongoDB;
- processa sequencialmente ate `batch-size` por ciclo;
- usa lease individual por evento;
- recupera eventos presos em `PROCESSING`;
- reaproveita snapshot por TTL configuravel;
- classifica respostas `201`, `200`, `409` e falhas permanentes/transitorias;
- usa retry com backoff e limite de tentativas;
- preserva payload bruto, `messageId`, `producerId`, timestamps e contexto.

### Reprocessamento Administrativo

Administradores podem recolocar eventos em `RECEIVED` sem editar payload bruto e sem chamar a ingestao canonica dentro da requisicao:

```text
POST /api/telemetry/events/{id}/reprocess
```

O reprocessamento e permitido somente para `CANONICAL_FAILED`, `CANONICAL_CONFLICT` e `DISCARDED`. O historico embutido preserva estado anterior, erro, tentativas, ids canonicos, usuario, data e motivo.

### Agregacoes Assincronas

O `Procel-API` permite criar jobs administrativos para dividir um periodo em janelas deterministicas:

```text
POST /api/analytics/aggregation-jobs
GET  /api/analytics/aggregation-jobs/{id}
```

A etapa atual inclui orquestracao, claim atomico de janelas, lease, retry, retomada e persistencia de buckets numericos por janela. A API ampla de consulta analitica e a interface de graficos ainda nao existem.

### Consulta Analitica

Os buckets numericos sao persistidos em `analytics_numeric_bucket`, agrupando `ParametroValor.numericValue` por:

```text
sensor_external_id + parametro_def_id + bucket_start + bucket_end + aggregation_version
```

O intervalo e semiaberto: `timestamp >= bucket_start` e `timestamp < bucket_end`. Valores booleanos e textuais nao sao agregados nesta etapa.

A API de consulta analitica expoe:

```text
GET /api/analytics/numeric-buckets
GET /api/analytics/numeric-buckets/summary
```

O endpoint de listagem retorna buckets persistidos e paginados. O endpoint de
summary consolida somente buckets existentes, sem consultar `medicao` ou
`parametro_valor`, usando media ponderada por `sampleCount`, menor minimo, maior
maximo e soma das amostras.

O `Procel-Admin` possui a rota `/analiticos`, visivel para `ADMIN`, `OPERADOR`
e `ANALISTA`. A tela oferece filtros por periodo obrigatorio, sensor, parametro,
compartimento, versao de agregacao e tamanho da pagina; cards de resumo por
grupo retornado pelo backend; grafico temporal de `averageValue`; e tabela de
buckets com paginacao server-side. O grafico usa apenas a pagina atual para nao
buscar milhares de pontos silenciosamente. Calculos analiticos permanecem no
backend.

## Bancos

### PostgreSQL

O PostgreSQL armazena dominio canonico: pessoas, cursos, disciplinas, presencas, sensores, medicoes, parametros, regras, perfis de integracao, metadata de ingestao, jobs de agregacao, janelas e buckets numericos.

Flyway e a fonte de verdade do schema. Migrations antigas nao devem ser alteradas.

### MongoDB

O MongoDB armazena eventos brutos em `raw_telemetry_events`. A collection possui indice unico de idempotencia bruta, indices operacionais por estado/sensor/data e TTL por `expiresAt`.

## Autenticacao e Roles

| Role | Uso |
| --- | --- |
| `ADMIN` | Administracao geral, integracoes, telemetria e criacao de jobs |
| `OPERADOR` | Operacao funcional e criacao de jobs analiticos |
| `ANALISTA` | Consulta de jobs analiticos |
| `INGESTOR` | Ingestao canonica e por perfil |
| `TELEMETRY_SERVICE` | Rotas internas usadas exclusivamente pelo `Procel-Telemetry` |
| `USUARIO` | Acesso basico ao console |

O JWT de servico usado pelo worker e curto, assinado com segredo configurado e nunca deve ser registrado em logs.

## Variaveis de Ambiente

### Procel-API

| Variavel | Finalidade |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario do PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Senha do PostgreSQL |
| `PROCEL_JWT_SECRET` | Segredo JWT de usuarios e servicos |
| `PROCEL_JWT_EXPIRATION_MINUTES` | TTL do JWT de usuario |
| `PROCEL_BOOTSTRAP_ADMIN_EMAIL` | Email do admin inicial |
| `PROCEL_BOOTSTRAP_ADMIN_PASSWORD` | Senha do admin inicial |
| `PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS` | Origens permitidas |
| `PROCEL_ANALYTICS_AGGREGATION_WORKER_ENABLED` | Habilita worker de agregacao |
| `PROCEL_ANALYTICS_AGGREGATION_VERSION` | Versao logica do algoritmo de buckets |

### Procel-Telemetry

| Variavel | Finalidade |
| --- | --- |
| `SPRING_MONGODB_URI` / `SPRING_DATA_MONGODB_URI` | URI MongoDB, incluindo database |
| `PROCEL_TELEMETRY_MAX_PAYLOAD_BYTES` | Limite do payload bruto |
| `PROCEL_TELEMETRY_RETENTION_DAYS` | Retencao TTL |
| `PROCEL_TELEMETRY_CANONICAL_WORKER_ENABLED` | Habilita worker canonico |
| `PROCEL_API_BASE_URL` | Base URL do `Procel-API` |
| `PROCEL_TELEMETRY_SERVICE_JWT_SUBJECT` | Subject do JWT de servico |
| `PROCEL_TELEMETRY_SERVICE_JWT_SECRET` | Segredo do JWT de servico |
| `PROCEL_TELEMETRY_SERVICE_JWT_TTL` | TTL do JWT de servico |
| `PROCEL_TELEMETRY_MQTT_ENABLED` | Habilita entrada MQTT |
| `PROCEL_TELEMETRY_MQTT_BROKER_URL` | URL do broker MQTT |
| `PROCEL_TELEMETRY_MQTT_USERNAME` | Usuario MQTT |
| `PROCEL_TELEMETRY_MQTT_PASSWORD` | Senha MQTT |
| `PROCEL_TELEMETRY_MQTT_TLS_ENABLED` | TLS MQTT |

### Procel-Admin

| Variavel | Finalidade |
| --- | --- |
| `API_BASE_URL` | Injetada pelo entrypoint Docker em runtime para o `Procel-API` |
| `VITE_TELEMETRY_API_URL` | URL da Telemetry em build-time |

O entrypoint Docker atual injeta `API_BASE_URL`, mas nao injeta `TELEMETRY_API_URL` em runtime. Para trocar a URL da Telemetry no container atual, a variavel deve estar definida no build do frontend ou o entrypoint precisa ser evoluido em etapa separada.

## Execucao Local

```bash
cd Procel-API
./mvnw test
./mvnw spring-boot:run
```

```bash
cd Procel-Telemetry
../Procel-API/mvnw -f pom.xml test
../Procel-API/mvnw -f pom.xml spring-boot:run
```

```bash
cd Procel-Admin
npm ci
npm run dev
npm run lint
npm run test
npm run build
```

Swagger:

```text
Procel-API:       http://localhost:8080/docs
Procel-Telemetry: http://localhost:8081/docs
```

## Docker e Deploy

Cada aplicacao possui Dockerfile proprio. `Procel-API/compose.yaml` sobe somente PostgreSQL local (`postgres:16`) e `Procel-Telemetry/compose.yaml` sobe somente MongoDB local (`mongo:7`, sem autenticacao). Em Coolify, configure cada servico apontando para o diretorio correto:

| Aplicacao | Diretorio de build |
| --- | --- |
| API | `Procel-API` |
| Telemetry | `Procel-Telemetry` |
| Admin | `Procel-Admin` |

Nao use secrets padrao em producao. A URI do MongoDB deve incluir o database, por exemplo:

```text
mongodb://usuario:senha@host:27017/procel_telemetry?authSource=admin&directConnection=true
```

## Testes

`Procel-API` usa Testcontainers para PostgreSQL em testes de integracao. `Procel-Telemetry` usa Testcontainers para MongoDB e HiveMQ nos testes MQTT. Quando Docker nao estiver disponivel, os testes dependentes de containers podem falhar ou ser ignorados conforme a configuracao do teste.

## Postman e Insomnia

Contratos manuais ficam em:

```text
API-Doc/Postman/
API-Doc/Insomnia/
```

As colecoes cobrem autenticacao, ingestao canonica, integracoes, snapshot, telemetria bruta, reprocessamento, jobs de agregacao e consultas de progresso.

## Documentacao Adicional

```text
Documentos/ArquiteturaGeral/
Documentos/DER-BancoAnalitico/
Documentos/Modelo-MongoDB/
Documentos/MQTT.md
Documentos/Seguranca-e-Operacao.md
Documentos/Catalogo-Dados.md
```

## Limitacoes Conhecidas

- A tela de analises limita o grafico a pagina atual dos buckets; uma API dedicada
  para series temporais densas fica reservada para a etapa 12.
- Observabilidade completa, rate limiting, backup automatizado e E2E integrado ficam reservados para a etapa 12.
- O `Procel-Admin` ainda nao injeta `TELEMETRY_API_URL` em runtime pelo entrypoint Docker.
- `spring.jpa.hibernate.ddl-auto=update` ainda aparece na configuracao local da API, mas a criacao do schema deve ser feita por Flyway.

## Roadmap

| Etapa | Descricao | Estado |
| --- | --- | --- |
| 1 | Ingestao canonica e idempotente | Concluida |
| 2 | Perfis, parsers e bindings | Concluida |
| 3 | Console administrativo | Concluida |
| 4 | Armazenamento bruto no MongoDB | Concluida |
| 5 | Worker `Telemetry -> Procel-API` | Concluida |
| 6 | Entrada MQTT | Concluida |
| 7 | Reprocessamento e operacao administrativa | Concluida |
| 8 | Agregacoes assincronas por periodo | Concluida |
| 9 | Buckets e medias analiticas | Concluida |
| 10 | API de consulta analitica | Concluida |
| 11 | Interface analitica e graficos | Concluida |
| 12 | Deploy integrado, observabilidade, seguranca e E2E | Parcial |

Progresso atual: **91,7%** (`11/12`).

Etapa atual: **11 - Interface analitica e graficos**.

Proxima etapa: finalizar deploy integrado, observabilidade, seguranca operacional e E2E.
