# PROBEN

PROBEN e uma plataforma para receber telemetria bruta, transformar eventos em medicoes canonicas, administrar sensores e perfis de integracao, e preparar processamento analitico por periodos.

O repositorio contem tres aplicacoes principais:

| Aplicacao | Responsabilidade | Banco |
| --- | --- | --- |
| `Proben-API` | API principal, autenticacao, dominio academico, sensores, medicoes canonicas, perfis de integracao e jobs analiticos | PostgreSQL |
| `Proben-Telemetry` | Recebimento bruto REST/MQTT, idempotencia bruta, armazenamento no MongoDB, worker canonico para envio ao `Proben-API` e operacao administrativa da telemetria | MongoDB |
| `Proben-Admin` | Console web administrativo para catalogo, sensores, regras, integracoes, sincronizacoes, analises e operacao da telemetria | N/A |

## Estrutura

```text
.
|-- Proben-API/          # Spring Boot, PostgreSQL, Flyway, dominio canonico
|-- Proben-Telemetry/    # Spring Boot, MongoDB, MQTT 5, worker canonico
|-- Proben-Admin/        # React/Vite, console administrativo
|-- API-Doc/             # Postman, Insomnia e orientacao dos contratos
|-- Database/            # SQL legado e consultas auxiliares
|-- Documentos/          # Arquitetura, DER, MongoDB, MQTT, seguranca e catalogo de dados
`-- README.md
```

## Tecnologias

| Area | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 4.0.8, Spring Security, Spring Data JPA/MongoDB |
| Banco canonico | PostgreSQL, Flyway |
| Banco bruto | MongoDB |
| MQTT | MQTT 5, Eclipse Paho MQTT v5, QoS 1 |
| Frontend | React 19, TypeScript, Vite 7, MUI 7, MUI X Charts, React Query |
| Testes | JUnit 5, Testcontainers, Vitest, Testing Library |
| Documentacao API | springdoc OpenAPI, Postman 2.1, Insomnia YAML |

## Arquitetura Atual

Dispositivos e produtores podem enviar telemetria bruta por REST ou MQTT para o `Proben-Telemetry`. O evento bruto e persistido em `raw_telemetry_events` no MongoDB, com payload original, hash, identidade do produtor, `messageId`, timestamps e estado de processamento.

O worker canonico do `Proben-Telemetry`, desabilitado por padrao, faz claim atomico de eventos `RECEIVED`, consulta o snapshot de integracao do `Proben-API`, seleciona o perfil/parser sem resolver ambiguidades silenciosamente e chama a rota interna de ingestao do `Proben-API` com JWT curto de role fixa `TELEMETRY_SERVICE`.

O `Proben-API` persiste medicoes canonicas no PostgreSQL, com metadata de ingestao, perfis de integracao, versoes de parser, bindings, jobs de agregacao, buckets numericos, motor de eventos de missoes, janelas temporais, atividades ciclicas e ledger de XP. O `Proben-Admin` consome `Proben-API` e `Proben-Telemetry` usando o JWT do usuario autenticado.

`Proben-Telemetry` nao acessa diretamente o PostgreSQL.

## Fluxos

### Ingestao REST Canonica

Clientes autorizados como `ADMIN` ou `INGESTOR` podem enviar medicoes canonicas diretamente ao `Proben-API`:

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

O `Proben-Telemetry` assina, quando habilitado:

```text
proben/telemetry/v1/+/+/events
proben/telemetry/v1/+/events
```

O `producerId` vem exclusivamente do topico. O `sensorId` do topico prevalece sobre o envelope; divergencia entre topico e envelope e descartada com ACK. O `messageId` e obrigatorio no envelope JSON e nao usa o packet identifier MQTT. O ACK manual ocorre somente depois de persistencia, duplicata equivalente, conflito idempotente ou descarte permanente. Falha transitoria do MongoDB nao recebe ACK para permitir redelivery.

### Worker Telemetry -> Proben-API

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

O `Proben-API` permite criar jobs administrativos para dividir um periodo em janelas deterministicas:

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

O `Proben-Admin` possui a rota `/analiticos`, visivel para `ADMIN`, `OPERADOR`
e `ANALISTA`. A tela oferece filtros por periodo obrigatorio, sensor, parametro,
compartimento, versao de agregacao e tamanho da pagina; cards de resumo por
grupo retornado pelo backend; grafico temporal de `averageValue`; e tabela de
buckets com paginacao server-side. O grafico usa apenas a pagina atual para nao
buscar milhares de pontos silenciosamente. Calculos analiticos permanecem no
backend.

### Missoes, Eventos e XP

O catalogo de missoes possui eventos configuraveis por `EventoDefinicao` e
`EventoCondicao`. O processamento instantaneo usa `SimpleMissionRuleEngine` por
padrao. O Drools permanece opt-in e e usado somente no fluxo temporal quando as
flags temporais estao habilitadas.

O motor persiste `EventoAvaliacaoRequest`, `EventoOcorrencia` e evidencias. Para
eventos temporais, `EventoJanelaAvaliacao` e suas evidencias permitem recuperar
janelas `DURACAO`, `TRANSICAO` e `JANELA_ENCERRADA` depois de reinicio. Quando
habilitado por flag, ocorrencias confirmadas processam beneficiarios academicos,
atividades ciclicas, progresso, conclusao automatica e XP de forma idempotente.

O saldo de XP nao fica salvo em `Pessoa`; ele e calculado pela soma append-only
de `xp_lancamento`. Conclusoes manuais continuam sem concessao automatica de XP.

Endpoints administrativos de operacao do motor:

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

Consulta de XP:

```text
GET /api/pessoas/{pessoaId}/xp
GET /api/pessoas/{pessoaId}/xp/lancamentos
```

## Bancos

### PostgreSQL

O PostgreSQL armazena dominio canonico: pessoas, cursos, disciplinas, presencas, sensores, medicoes, parametros, regras, perfis de integracao, metadata de ingestao, jobs de agregacao, janelas e buckets numericos, missoes, eventos, janelas temporais, ocorrencias, atividades e ledger de XP.

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
| `TELEMETRY_SERVICE` | Rotas internas usadas exclusivamente pelo `Proben-Telemetry` |
| `USUARIO` | Acesso basico ao console |

O JWT de servico usado pelo worker e curto, assinado com segredo configurado e nunca deve ser registrado em logs.

## Variaveis de Ambiente

### Proben-API

| Variavel | Finalidade |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario do PostgreSQL |
| `SPRING_DATASOURCE_PASSWORD` | Senha do PostgreSQL |
| `PROBEN_JWT_SECRET` | Segredo JWT de usuarios e servicos |
| `PROBEN_JWT_EXPIRATION_MINUTES` | TTL do JWT de usuario |
| `PROBEN_BOOTSTRAP_ADMIN_EMAIL` | Email do admin inicial |
| `PROBEN_BOOTSTRAP_ADMIN_PASSWORD` | Senha do admin inicial |
| `PROBEN_CORS_ALLOWED_ORIGIN_PATTERNS` | Origens permitidas |
| `PROBEN_ANALYTICS_AGGREGATION_WORKER_ENABLED` | Habilita worker de agregacao |
| `PROBEN_ANALYTICS_AGGREGATION_VERSION` | Versao logica do algoritmo de buckets |
| `PROBEN_SHARED_CONTEXT_COLLECTOR_ENABLED` | Habilita a coleta HTTP de observacoes externas compartilhadas |
| `PROBEN_SHARED_CONTEXT_COLLECTOR_POLL_INTERVAL` | Frequencia de procura por integracoes vencidas (a periodicidade de cada fonte fica no banco) |
| `PROBEN_MISSIONS_EVALUATION_WORKER_ENABLED` | Habilita worker de avaliacao instantanea de eventos de missoes |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ENABLED` | Habilita infraestrutura de janelas temporais |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_WORKER_ENABLED` | Habilita worker temporal |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_DROOLS_ENABLED` | Habilita Drools no fluxo temporal |
| `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ACTIVITIES_ENABLED` | Habilita efeitos de atividades/XP para ocorrencias temporais satisfeitas |

### Proben-Telemetry

| Variavel | Finalidade |
| --- | --- |
| `SPRING_MONGODB_URI` / `SPRING_DATA_MONGODB_URI` | URI MongoDB, incluindo database |
| `PROBEN_TELEMETRY_MAX_PAYLOAD_BYTES` | Limite do payload bruto |
| `PROBEN_TELEMETRY_RETENTION_DAYS` | Retencao TTL |
| `PROBEN_TELEMETRY_CANONICAL_WORKER_ENABLED` | Habilita worker canonico |
| `PROBEN_API_BASE_URL` | Base URL do `Proben-API` |
| `PROBEN_TELEMETRY_SERVICE_JWT_SUBJECT` | Subject do JWT de servico |
| `PROBEN_TELEMETRY_SERVICE_JWT_SECRET` | Segredo do JWT de servico |
| `PROBEN_TELEMETRY_SERVICE_JWT_TTL` | TTL do JWT de servico |
| `PROBEN_TELEMETRY_MQTT_ENABLED` | Habilita entrada MQTT |
| `PROBEN_TELEMETRY_MQTT_BROKER_URL` | URL do broker MQTT |
| `PROBEN_TELEMETRY_MQTT_USERNAME` | Usuario MQTT |
| `PROBEN_TELEMETRY_MQTT_PASSWORD` | Senha MQTT |
| `PROBEN_TELEMETRY_MQTT_TLS_ENABLED` | TLS MQTT |

### Proben-Admin

| Variavel | Finalidade |
| --- | --- |
| `API_BASE_URL` | Injetada pelo entrypoint Docker em runtime para o `Proben-API` |
| `VITE_TELEMETRY_API_URL` | URL da Telemetry em build-time |

O entrypoint Docker atual injeta `API_BASE_URL`, mas nao injeta `TELEMETRY_API_URL` em runtime. Para trocar a URL da Telemetry no container atual, a variavel deve estar definida no build do frontend ou o entrypoint precisa ser evoluido em etapa separada.

## Execucao Local

```bash
cd Proben-API
./mvnw test
./mvnw spring-boot:run
```

```bash
cd Proben-Telemetry
../Proben-API/mvnw -f pom.xml test
../Proben-API/mvnw -f pom.xml spring-boot:run
```

```bash
cd Proben-Admin
npm ci
npm run dev
npm run lint
npm run test
npm run build
```

Swagger:

```text
Proben-API:       http://localhost:8080/docs
Proben-Telemetry: http://localhost:8081/docs
```

## Docker e Deploy

Existem dois modos oficiais de deploy.

### Modo 1 - Stack integrado

Executado pela raiz:

```bash
docker compose up -d
```

Esse modo sobe PostgreSQL, MongoDB, broker MQTT, `Proben-API`,
`Proben-Telemetry` e `Proben-Admin`. Ele e destinado a desenvolvimento local,
homologacao, testes E2E e validacao da arquitetura completa. Os hostnames
internos `postgres`, `mongo`, `mqtt` e `proben-api` aparecem somente no
`compose.yaml` integrado e em exemplos de ambiente desse modo.

Validacao recomendada:

```bash
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

### Modo 2 - Aplicacoes independentes

Cada aplicacao mantem Dockerfile proprio e pode ser construida diretamente a
partir do seu diretorio:

```bash
docker build -t proben-api ./Proben-API
docker build -t proben-telemetry ./Proben-Telemetry
docker build -t proben-admin ./Proben-Admin
```

As imagens recebem dependencias externas por variaveis de ambiente, nao dependem
do Compose da raiz e expoem somente a propria porta. Bancos, broker e aplicacoes
podem estar no mesmo Compose, em recursos separados do Coolify, em servicos
gerenciados ou em hosts diferentes.

`Proben-API/compose.yaml` fornece ambiente minimo da API com PostgreSQL.
`Proben-Telemetry/compose.yaml` fornece ambiente minimo da Telemetry com MongoDB
e MQTT. Esses composes independentes nao dependem do Compose da raiz.

Precedencia de configuracao:

1. variavel fornecida pela plataforma;
2. variavel declarada no Compose;
3. default exclusivamente local no `application.yml`, Dockerfile ou entrypoint.

Arquivos de exemplo:

```text
.env.example
.env.integrated.example
Proben-API/.env.example
Proben-Telemetry/.env.example
Proben-Admin/.env.example
```

Nao versionar `.env` real.

### Coolify

Publique cada modulo como recurso independente quando bancos, broker e
aplicacoes forem separados.

| Aplicacao | Diretorio de build |
| --- | --- |
| API | `Proben-API` |
| Telemetry | `Proben-Telemetry` |
| Admin | `Proben-Admin` |

Configuracao sugerida:

| Aplicacao | Base directory | Dockerfile | Healthcheck |
| --- | --- | --- | --- |
| Proben-API | `/Proben-API` | `/Dockerfile` | `/actuator/health` |
| Proben-Telemetry | `/Proben-Telemetry` | `/Dockerfile` | `/actuator/health` |
| Proben-Admin | `/Proben-Admin` | `/Dockerfile` | `/healthz` |

Variaveis minimas da API:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
PROBEN_JWT_SECRET
PROBEN_TELEMETRY_SERVICE_JWT_SECRET
PROBEN_CORS_ALLOWED_ORIGIN_PATTERNS
```

Variaveis minimas da Telemetry:

```text
SPRING_MONGODB_URI
PROBEN_API_BASE_URL
PROBEN_JWT_SECRET
PROBEN_TELEMETRY_SERVICE_JWT_SECRET
PROBEN_TELEMETRY_MQTT_ENABLED
PROBEN_TELEMETRY_MQTT_BROKER_URL
PROBEN_TELEMETRY_MQTT_USERNAME
PROBEN_TELEMETRY_MQTT_PASSWORD
PROBEN_TELEMETRY_MQTT_TLS_ENABLED
```

Variaveis minimas do Admin:

```text
API_BASE_URL
TELEMETRY_API_URL
```

Dominios sugeridos: `api.seu-dominio`, `telemetry.seu-dominio` e
`admin.seu-dominio`. Configure CORS na API e na Telemetry para permitir o
dominio externo do Admin. `PROBEN_API_BASE_URL` na Telemetry deve usar a URL
interna do recurso API quando estiverem na mesma rede privada do Coolify, ou a
URL externa HTTPS quando estiverem em redes diferentes. `API_BASE_URL` e
`TELEMETRY_API_URL` no Admin devem ser URLs acessiveis pelo navegador do usuario.

Ordem recomendada: publicar PostgreSQL e MongoDB, publicar Proben-API, validar
`/actuator/health`, publicar broker MQTT se usado, publicar Proben-Telemetry,
validar `/actuator/health`, publicar Proben-Admin e validar `/healthz`.

Teste isolado: acesse healthcheck de cada modulo, faca login no Admin contra a
API configurada, liste eventos de Telemetry se `TELEMETRY_API_URL` estiver
habilitada e valide a tela `/analiticos` contra a API.

Nao use secrets padrao em producao. A URI do MongoDB deve incluir o database, por exemplo:

```text
mongodb://usuario:senha@host:27017/proben_telemetry?authSource=admin&directConnection=true
```

## Testes

`Proben-API` usa Testcontainers para PostgreSQL em testes de integracao. `Proben-Telemetry` usa Testcontainers para MongoDB e HiveMQ nos testes MQTT. Quando Docker nao estiver disponivel, os testes dependentes de containers podem falhar ou ser ignorados conforme a configuracao do teste.

## Postman e Insomnia

Contratos manuais ficam em:

```text
API-Doc/Postman/
API-Doc/Insomnia/
```

As colecoes cobrem autenticacao, ingestao canonica, integracoes, snapshot, telemetria bruta, reprocessamento, jobs de agregacao, consultas analiticas e consultas de progresso.

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
- Rate limiting, backup automatizado e E2E integrado de staging/producao ficam reservados para evolucao operacional da etapa 12.
- `spring.jpa.hibernate.ddl-auto=update` ainda aparece na configuracao local da API, mas a criacao do schema deve ser feita por Flyway.

## Roadmap

| Etapa | Descricao | Estado |
| --- | --- | --- |
| 1 | Ingestao canonica e idempotente | Concluida |
| 2 | Perfis, parsers e bindings | Concluida |
| 3 | Console administrativo | Concluida |
| 4 | Armazenamento bruto no MongoDB | Concluida |
| 5 | Worker `Telemetry -> Proben-API` | Concluida |
| 6 | Entrada MQTT | Concluida |
| 7 | Reprocessamento e operacao administrativa | Concluida |
| 8 | Agregacoes assincronas por periodo | Concluida |
| 9 | Buckets e medias analiticas | Concluida |
| 10 | API de consulta analitica | Concluida |
| 11 | Interface analitica e graficos | Concluida |
| 12 | Deploy integrado, observabilidade, seguranca, motor de missoes e E2E | Parcial |

Progresso atual: **91,7%** (`11/12`).

Etapa atual: **11 - Interface analitica e graficos**.

Proxima etapa: finalizar deploy integrado, observabilidade, seguranca operacional e E2E.
