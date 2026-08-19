# Procel-Admin

`Procel-Admin` e o console web administrativo do PROCEL.

## Stack

- React 19;
- TypeScript 5.9;
- Vite 7;
- MUI 7;
- React Router 7;
- React Query 5;
- Vitest e Testing Library.

## Rotas

| Rota | Uso | Roles |
| --- | --- | --- |
| `/login` | Autenticacao | publica |
| `/` | Visao geral | autenticado |
| `/catalogo` | Navegador de dados | autenticado |
| `/operacoes` | Operacoes da API | autenticado |
| `/disciplinas` | Minhas disciplinas | `USUARIO`, `ADMIN`, `OPERADOR`, `ANALISTA` |
| `/sensores` | Sensores e regras | `ADMIN`, `OPERADOR` |
| `/missoes` | Missoes e atividades | `ADMIN`, `OPERADOR` |
| `/sincronizacoes` | Sincronizacoes de salas/aulas | `ADMIN`, `OPERADOR` |
| `/integracoes` | Perfis de integracao | `ADMIN` |
| `/integracoes/perfis/:profileId` | Detalhe de perfil | `ADMIN` |
| `/integracoes/snapshot` | Snapshot de integracao | `ADMIN` |
| `/telemetria` | Operacao administrativa da telemetria | `ADMIN` |

## Funcionalidades

- Login JWT;
- catalogo de pessoas, cursos, disciplinas, compartimentos e sensores;
- administracao de sensores e regras;
- missoes e atividades;
- sincronizacao de salas e aulas;
- integracoes, perfis, parser versions, bindings e snapshot;
- listagem, detalhe e reprocessamento de eventos brutos da Telemetry.

A interface analitica com graficos ainda nao foi implementada.

## APIs

O cliente principal usa `API_BASE_URL` em runtime via `window.__PROCEL_CONFIG__.API_BASE_URL`, com fallback local.

A Telemetry usa `VITE_TELEMETRY_API_URL` em build-time ou `window.__PROCEL_CONFIG__.TELEMETRY_API_URL` se existir. O entrypoint Docker atual injeta somente `API_BASE_URL`; ele ainda nao injeta `TELEMETRY_API_URL` em runtime.

O JWT autenticado e enviado para `Procel-API` e `Procel-Telemetry`.

## Docker/Nginx

O Dockerfile faz build com Node 24 Alpine e publica arquivos estaticos em Nginx Alpine. O script `docker-entrypoint.d/40-runtime-config.sh` gera `config.js` em runtime com `API_BASE_URL`.

## Execucao

```bash
npm ci
npm run dev
npm run lint
npm run test
npm run build
```

## Pendencias Conhecidas

- Injetar `TELEMETRY_API_URL` em runtime no entrypoint Docker.
- Implementar interface analitica e graficos somente apos a API de consulta analitica.
