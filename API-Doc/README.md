# API-Doc

Esta pasta contem contratos manuais para testes e validacao das APIs PROBEN.

## Estrutura

```text
API-Doc/
|-- Contratos-HTTP.md
|-- Postman/
|   |-- PROBEN-API/
|   `-- Cobalto/
`-- Insomnia/
    `-- Insomnia_2026-05-12_v5_no_bom.yaml
```

## Swagger/OpenAPI

As especificacoes vivas sao geradas pelos backends:

```text
Proben-API:       /v3/api-docs e /docs
Proben-Telemetry: /v3/api-docs e /docs
```

O resumo manual dos contratos esta em `Contratos-HTTP.md`.

## Postman

`Postman/PROBEN-API/PROBEN-API-E2E.postman_collection.json` contem o fluxo E2E manual existente. `PROBEN-API-Env.postman_environment.json` define variaveis locais, sem credenciais de producao. O export Insomnia versionado mantem requests manuais. A cobertura dessas colecoes nao e completa; utilize `/v3/api-docs` para explorar e validar os contratos atuais. Inclua nas proximas atualizacoes das colecoes:

- health;
- login;
- ingestao canonica;
- duplicata equivalente;
- conflito idempotente;
- perfis, versoes e bindings;
- snapshot;
- ingestao externa por perfil;
- Telemetry REST;
- listagem, detalhe e reprocessamento;
- jobs de agregacao, consulta de progresso e consultas analiticas.
- contextos compartilhados, integracoes/coletas HTTP e observacoes externas.
- regras ambientais, resolucao, execucoes e operacao.
- campus/coordenadas e sincronizacao de salas e aulas.

Nao versione credenciais de producao ou tokens reais.

## Insomnia

`Insomnia/Insomnia_2026-05-12_v5_no_bom.yaml` mantem contratos equivalentes em YAML.

## Observacao

As colecoes manuais nao sao um inventario completo e podem ficar defasadas entre
atualizacoes. Use `/v3/api-docs` como fonte de verdade para schemas, parametros,
status e rotas, especialmente para regras ambientais, contextos compartilhados,
sincronizacoes e operacao do motor de missoes. Atualize as colecoes junto com
mudancas de contrato que integrem os fluxos de smoke/regressao.
