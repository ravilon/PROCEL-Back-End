# Organizacao do codigo

## Visao dos modulos

| Modulo | Organizacao atual | Responsabilidade |
| --- | --- | --- |
| `Proben-API` | Spring Boot em `com.proben.api`, organizado por camadas e subdominio | Contratos HTTP, regras de negocio, persistencia PostgreSQL, workers e integracoes externas |
| `Proben-Telemetry` | Spring Boot em `com.proben.telemetry`, organizado por camada e fluxo | Recepcao bruta, MQTT, MongoDB, reprocessamento e entrega canonica |
| `Proben-Admin` | React/TypeScript; `api`, `auth`, `components`, `features`, `pages`, `types` | Console administrativo e clientes HTTP |

## Estrutura interna

Na API, `controller`, `dto`, `entity` e `repository` sao separados por dominio (`analytics`, `missions`, `people`, `rooms`, `sensors`, `shared`, entre outros). `service` concentra casos de uso e workers; `integration` contem adaptadores externos; `config`, `security`, `observability` e `exception` sao preocupacoes transversais. A Telemetry separa controllers, DTOs, documentos Mongo, repositorio, seguranca, observabilidade e servicos `canonical`/`mqtt`.

No Admin, `pages` organiza telas de alto nivel, enquanto `features` agrupa componentes e comportamento de dominios maiores. `src/api` contem clientes por recurso, `types` os contratos TS e `auth` a sessao/controle de acesso.

## Avaliacao e recomendacoes

- A separacao dos tres deployables e coerente com os limites de persistencia: somente API acessa PostgreSQL; Telemetry e dona dos eventos brutos em MongoDB; Admin acessa os backends por HTTP.
- A API combina organizacao por camadas com pastas por dominio. Alguns fluxos novos, como coletas HTTP e regras ambientais, ja possuem controllers/services proprios; mantenha essa divisao e evite ampliar controllers monoliticos.
- `SharedContextService` e `SharedContextCollector` concentram bastante comportamento e SQL. Em futuras mudancas, considerar extrair componentes de persistencia, validacao/mapeamento e transporte HTTP para facilitar revisao e testes sem alterar contratos.
- `Proben-Admin/src/App.tsx` declara rotas e `api` tem clientes por recurso; para novos dominios, manter telas, tipos, cliente HTTP e testes juntos em `features/<dominio>` quando houver UI complexa.
- Migrations Flyway sao a fonte de verdade do PostgreSQL. Nao editar migrations ja aplicadas; adicionar nova migration e manter os diagramas explicitamente identificados como completos ou parciais.
- Foi iniciada documentacao Javadoc nos pacotes de ingestao, analytics, missoes, contexto compartilhado, MQTT e entrega canonica. Ainda nao existe uma rotina configurada de publicacao em CI; ampliar gradualmente a documentacao de classes/metodos publicos e workers, cobrindo pre-condicoes, retry, idempotencia, concorrencia e efeitos colaterais.
- Existem contratos manuais de API e OpenAPI gerado em runtime. Tratar OpenAPI e colecoes como artefatos que precisam acompanhar mudancas; evitar usar apenas uma tabela manual extensa como fonte de verdade.

## Navegacao recomendada

1. Comece pelo README da raiz para topologia e execucao local.
2. Consulte o README do modulo para configuracao e responsabilidades locais.
3. Consulte `API-Doc/Contratos-HTTP.md` para resumo de endpoints e `/v3/api-docs` para o contrato vivo.
4. Consulte migrations Flyway para estrutura PostgreSQL e o modelo Mongo para eventos brutos.
