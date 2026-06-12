# PROCEL Admin

Console web independente para operacao e gerenciamento do PROCEL.

## Stack

- React
- TypeScript
- Vite
- Material UI
- TanStack Query
- Nginx

## Desenvolvimento local

```bash
npm install
npm run dev
```

Por padrao, o console usa a API em `http://localhost:8080`. Para alterar durante
o desenvolvimento, edite `public/config.js`.

## Build

```bash
npm run build
```

## Docker

```bash
docker build -t procel-admin .
docker run --rm -p 8081:80 \
  -e API_BASE_URL=https://api.exemplo.com \
  procel-admin
```

`API_BASE_URL` e aplicada em runtime pelo entrypoint do Nginx. A imagem nao
precisa ser reconstruida quando apenas a URL da API mudar.

## Coolify

Crie uma aplicacao separada para o console:

```text
Base directory: /Procel-Admin
Dockerfile: /Dockerfile
Port: 80
Health check: /healthz
Watch paths: /Procel-Admin/**
```

Configure:

```text
API_BASE_URL=https://api.seudominio.com
```

No backend, permita o dominio do console no CORS:

```text
PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS=https://admin.seudominio.com
```

Configuracao sugerida:

```text
admin.seudominio.com -> Procel-Admin
api.seudominio.com   -> Procel-Ingestion
```

Assim, alteracoes em `Procel-Admin` nao reiniciam o backend, e alteracoes em
`Procel-Ingestion` nao republicam o console.
