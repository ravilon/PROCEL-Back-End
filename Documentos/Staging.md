# Staging PROBEN

Este documento descreve como provisionar um staging seguro do PROBEN no Coolify para validar o fluxo temporal sem tocar em producao.

## Objetivo

Validar, em ambiente isolado, o caminho `Medicao -> EventoAvaliacaoRequest -> EventoJanelaAvaliacao DURACAO/TRANSICAO/JANELA_ENCERRADA -> avaliacao temporal, com Drools quando habilitado -> EventoOcorrencia`.

Atividades, beneficiarios e pontos so devem ser conectados quando
`PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ACTIVITIES_ENABLED=true` estiver
habilitada explicitamente em staging.

## Recursos Isolados

- Proben-API: servico proprio com URL de staging.
- Proben-Telemetry: servico proprio com URL de staging.
- PostgreSQL: banco e volume exclusivos; nomes devem conter `staging`.
- MongoDB: banco e volume exclusivos; nomes devem conter `staging`.
- MQTT: broker ou namespace exclusivo de staging.

Nunca reutilize banco, volume, broker, host ou credencial de producao.

## Variaveis Necessarias

Use `.env.staging.example` como contrato de variaveis. O arquivo versionado nao contem secrets reais.

Obrigatorias para a API:

- `SPRING_PROFILES_ACTIVE=staging`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `PROBEN_JWT_SECRET`
- `PROBEN_BOOTSTRAP_ADMIN_ENABLED=false`
- `PROBEN_CORS_ALLOWED_ORIGIN_PATTERNS`

Obrigatorias para Telemetry:

- `SPRING_PROFILES_ACTIVE=staging`
- `SPRING_MONGODB_URI`
- `PROBEN_API_BASE_URL`
- `PROBEN_JWT_SECRET`
- `PROBEN_TELEMETRY_SERVICE_JWT_SECRET`
- `PROBEN_TELEMETRY_CANONICAL_WORKER_ENABLED=true`
- `PROBEN_TELEMETRY_MQTT_ENABLED=true`, se MQTT for validado

Flags temporais da API para a validacao:

- `PROBEN_MISSIONS_RULE_ENGINE=simple`
- `PROBEN_MISSIONS_EVALUATION_WORKER_ENABLED=true`
- `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ENABLED=true`
- `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_WORKER_ENABLED=true`
- `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_DROOLS_ENABLED=true`
- `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ACTIVITIES_ENABLED=true`, somente quando a validacao incluir atividades/pontos

Para validacao rapida, use duracoes curtas:

- `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_MAXIMUM_SAMPLE_GAP=5s`
- `PROBEN_MISSIONS_DROOLS_MAXIMUM_SAMPLE_GAP=5s`
- `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_MAXIMUM_WINDOW_DURATION=10m`

## Ordem de Deploy no Coolify

1. Criar rede privada do stack de staging.
2. Criar PostgreSQL staging com database e volume contendo `staging` no nome.
3. Criar MongoDB staging com database e volume contendo `staging` no nome.
4. Criar MQTT staging, com TLS e credenciais proprias quando exposto fora da rede privada.
5. Publicar Proben-API com `SPRING_PROFILES_ACTIVE=staging`.
6. Validar `GET /actuator/health` da API.
7. Confirmar Flyway em `V33`.
8. Publicar Proben-Telemetry com `SPRING_PROFILES_ACTIVE=staging`.
9. Validar `GET /actuator/health` da Telemetry.
10. Validar `/actuator/prometheus` com credencial administrativa.
11. Rodar `scripts/validate-staging.ps1 -Target staging ...`.

## Validacao

O script `scripts/validate-staging.ps1` exige:

- `-Target staging`, sem valor padrao.
- URLs, hosts e databases informados explicitamente.
- credenciais vindas de variaveis de ambiente, sem impressao de secrets.
- confirmacao manual antes de escrever dados descartaveis.

Todos os dados criados pelo script usam prefixo `staging-temporal-`. A limpeza deve limitar-se a esse prefixo.

## Rollback

Rollback de aplicacao:

1. Desabilitar workers temporais:
   - `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_WORKER_ENABLED=false`
   - `PROBEN_MISSIONS_EVALUATION_TEMPORAL_WINDOWS_ENABLED=false`, se necessario.
2. Desabilitar worker canonico:
   - `PROBEN_TELEMETRY_CANONICAL_WORKER_ENABLED=false`
3. Reimplantar a versao anterior pelo Coolify.
4. Confirmar `/actuator/health`.

Rollback de dados:

- Nao apague dados fora do prefixo `staging-temporal-`.
- Para dados do script, desative missoes/eventos/sensores pelo endpoint administrativo ou delete manualmente apenas registros com prefixo comprovado.
- Nao altere migrations antigas.

## Limpeza de Dados

Escopo permitido:

- missoes cujo titulo comece com `staging-temporal-`;
- eventos cujo nome comece com `staging-temporal-`;
- sensores cujo `external_id` comece com `staging-temporal-`;
- medicoes ligadas a sensores `staging-temporal-*`;
- janelas/ocorrencias ligadas aos eventos `staging-temporal-*`.

Antes de qualquer limpeza destrutiva, gere contagem por tabela e revise os IDs.

## Criterios GO/NO-GO

GO para conectar atividades temporais de forma controlada:

- health da API e Telemetry `UP`;
- Flyway em `V33`;
- workers explicitamente habilitados apenas em staging;
- duracao satisfeita gera uma unica ocorrencia confirmada;
- interrupcao invalida janela sem ocorrencia;
- lacuna excessiva expira janela sem ocorrencia;
- duplicata canonica nao duplica medicao, request, janela, ocorrencia ou evidencia;
- retry e lease expirado recuperam sem processamento simultaneo;
- duas salas mantem isolamento;
- backlog volta para zero apos lote controlado;
- cache Drools apresenta hits depois do prewarm;
- se atividades/pontos estiverem habilitados, atividade, progresso, conclusao automatica e `pontos_lancamento` sao criados uma unica vez;
- sem erros persistentes nos contadores de falha;
- memoria e latencia dentro dos thresholds provisorios.

NO-GO:

- qualquer apontamento para producao;
- banco ou volume sem `staging` no nome;
- duplicacao de ocorrencia/evidencia;
- backlog crescente sem drenagem;
- retries aumentando continuamente;
- falha de compilacao Drools;
- latencia p95 acima do limite definido para staging;
- OOM, restart loop ou erro transitorio recorrente.

## Thresholds Provisorios

- backlog temporal: deve voltar a `0` em ate 2 minutos apos o lote.
- backlog canonico: deve voltar a `0` em ate 2 minutos apos o lote.
- retries temporais: `0` em cenario nominal; qualquer aumento exige analise.
- falhas Drools: `0`.
- cache Drools: pelo menos 1 hit depois do prewarm/segunda avaliacao.
- avaliacao Drools p95: menor que `250 ms` em staging pequeno.
- memoria: sem crescimento monotonicamente crescente apos soak de 15 minutos.

Estes thresholds sao iniciais e devem ser calibrados com carga realista.
