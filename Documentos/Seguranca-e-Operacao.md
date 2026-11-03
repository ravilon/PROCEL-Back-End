# Seguranca e Operacao

## Roles

| Role | Escopo |
| --- | --- |
| `ADMIN` | Administracao ampla, integracoes, telemetria e jobs analiticos |
| `OPERADOR` | Operacao de sensores, sincronizacoes e criacao de jobs |
| `ANALISTA` | Consulta de jobs analiticos |
| `INGESTOR` | Ingestao canonica e por perfil |
| `TELEMETRY_SERVICE` | Chamadas internas do `Proben-Telemetry` para o `Proben-API` |
| `USUARIO` | Funcionalidades basicas do console |

## JWT de Usuario

Usuarios fazem login por:

```text
POST /api/auth/login
```

O JWT e assinado com `PROBEN_JWT_SECRET`. O valor padrao de desenvolvimento nao deve ser usado em producao.

## JWT de Servico

O worker canonico do `Proben-Telemetry` gera JWT curto com role fixa `TELEMETRY_SERVICE`. Configure:

```text
PROBEN_TELEMETRY_SERVICE_JWT_SUBJECT
PROBEN_TELEMETRY_SERVICE_JWT_SECRET
PROBEN_TELEMETRY_SERVICE_JWT_TTL
```

O segredo deve ser compativel com o segredo aceito pelo `Proben-API`. Tokens e secrets nao devem ser registrados em logs.

## CORS

`Proben-API` e `Proben-Telemetry` usam configuracao por padrao de origem:

```text
PROBEN_CORS_ALLOWED_ORIGIN_PATTERNS
```

Use origens explicitas em producao.

## Bootstrap Admin

O `Proben-API` cria/garante admin inicial por configuracao:

```text
PROBEN_BOOTSTRAP_ADMIN_EMAIL
PROBEN_BOOTSTRAP_ADMIN_PASSWORD
PROBEN_BOOTSTRAP_ADMIN_NAME
```

Troque a senha padrao antes de expor o ambiente.

## MongoDB

Ambiente local pode usar MongoDB sem autenticacao. Em producao, use usuario/senha, `authSource` correto e database explicito na URI:

```text
mongodb://usuario:senha@host:27017/proben_telemetry?authSource=admin&directConnection=true
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
