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

## Organizacao do codigo

```text
src/api/       # Clients HTTP por dominio, sobre lib/api.ts
src/auth/      # Sessao, login e protecao de rotas
src/components/# Componentes estruturais compartilhados
src/features/  # Telas e componentes organizados por area funcional
src/lib/       # Infraestrutura comum do front-end
src/pages/     # Entradas de rota, finas, delegando para features
src/types/     # Contratos TypeScript separados por dominio
```

`src/features/catalog` concentra o navegador de dados em componentes menores
para compartimentos, disciplinas, cursos e pessoas. Novas telas devem preferir
clients em `src/api` em vez de montar URLs diretamente dentro do JSX.

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
api.seudominio.com   -> Procel-API
```

Assim, alteracoes em `Procel-Admin` nao reiniciam o backend, e alteracoes em
`Procel-API` nao republicam o console.
