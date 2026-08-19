# Seguranca e Operacao

## Roles

| Role | Escopo |
| --- | --- |
| `ADMIN` | Administracao ampla, integracoes, telemetria e jobs analiticos |
| `OPERADOR` | Operacao de sensores, sincronizacoes e criacao de jobs |
| `ANALISTA` | Consulta de jobs analiticos |
| `INGESTOR` | Ingestao canonica e por perfil |
| `TELEMETRY_SERVICE` | Chamadas internas do `Procel-Telemetry` para o `Procel-API` |
| `USUARIO` | Funcionalidades basicas do console |

## JWT de Usuario

Usuarios fazem login por:

```text
POST /api/auth/login
```

O JWT e assinado com `PROCEL_JWT_SECRET`. O valor padrao de desenvolvimento nao deve ser usado em producao.

## JWT de Servico

O worker canonico do `Procel-Telemetry` gera JWT curto com role fixa `TELEMETRY_SERVICE`. Configure:

```text
PROCEL_TELEMETRY_SERVICE_JWT_SUBJECT
PROCEL_TELEMETRY_SERVICE_JWT_SECRET
PROCEL_TELEMETRY_SERVICE_JWT_TTL
```

O segredo deve ser compativel com o segredo aceito pelo `Procel-API`. Tokens e secrets nao devem ser registrados em logs.

## CORS

`Procel-API` e `Procel-Telemetry` usam configuracao por padrao de origem:

```text
PROCEL_CORS_ALLOWED_ORIGIN_PATTERNS
```

Use origens explicitas em producao.

## Bootstrap Admin

O `Procel-API` cria/garante admin inicial por configuracao:

```text
PROCEL_BOOTSTRAP_ADMIN_EMAIL
PROCEL_BOOTSTRAP_ADMIN_PASSWORD
PROCEL_BOOTSTRAP_ADMIN_NAME
```

Troque a senha padrao antes de expor o ambiente.

## MongoDB

Ambiente local pode usar MongoDB sem autenticacao. Em producao, use usuario/senha, `authSource` correto e database explicito na URI:

```text
mongodb://usuario:senha@host:27017/procel_telemetry?authSource=admin&directConnection=true
```

Erro `Database name must not be empty` indica URI sem database. Erro `AuthenticationFailed` normalmente indica usuario, senha ou `authSource` incorreto.

## MQTT

Use TLS e ACL por produtor no broker. A aplicacao aceita credenciais apenas por properties/env e nao deve registrar senha ou payload completo.

## Health

Rotas de health ficam publicas:

```text
GET /actuator/health
```

## Retencao

Eventos brutos usam TTL via `expiresAt` em `raw_telemetry_events`. Medicoes canonicas e buckets analiticos nao possuem politica automatica de retencao implementada nesta etapa.

## Backups

Nao ha backup automatizado no codigo. Defina backup externo para PostgreSQL e MongoDB antes de producao.

## Itens Reservados para Etapa 12

- observabilidade completa;
- rate limiting;
- backup automatizado;
- hardening final de secrets;
- testes ponta a ponta integrados;
- deploy integrado documentado com ambientes reais.
