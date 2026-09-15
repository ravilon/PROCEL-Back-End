# API-Doc

Esta pasta contem contratos manuais para testes e validacao das APIs PROCEL.

## Estrutura

```text
API-Doc/
|-- Postman/
|   |-- PROCEL-API/
|   `-- Cobalto/
`-- Insomnia/
```

## Swagger/OpenAPI

As especificacoes vivas sao geradas pelos backends:

```text
Procel-API:       /v3/api-docs e /docs
Procel-Telemetry: /v3/api-docs e /docs
```

O resumo manual dos contratos esta em `Contratos-HTTP.md`.

## Postman

`Postman/PROCEL-API/PROCEL-API-E2E.postman_collection.json` contem chamadas para:

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

`Postman/PROCEL-API/PROCEL-API-Env.postman_environment.json` define variaveis locais. Nao ha credenciais de producao.

## Insomnia

`Insomnia/Insomnia_2026-05-12_v5_no_bom.yaml` mantem contratos equivalentes em YAML.

## Observacao

As colecoes nao devem inventar endpoints. Para contratos vivos, prefira sempre
`/v3/api-docs`, especialmente para rotas administrativas do motor de missoes e
consultas de XP, que evoluiram depois das colecoes manuais iniciais.
