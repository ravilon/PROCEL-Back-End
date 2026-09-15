# Catalogo de Dados

| Aplicacao proprietaria | Banco | Tabela/collection | Finalidade | Chave/identidade | Retencao | Produtor | Consumidor | Tipo | Observacoes de seguranca |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Procel-Telemetry | MongoDB | `raw_telemetry_events` | Evento bruto recebido por REST/MQTT | `producerId + source + messageId` | TTL por `expiresAt` | Produtores REST/MQTT | Worker canonico/Admin | Bruto | Payload readonly; nao registrar payload completo |
| Procel-API | PostgreSQL | `medicao` | Medicao canonica por sensor e timestamp | `id` | Sem TTL automatico | Ingestao canonica/parser | Consultas e analytics | Canonico | Vinculada a metadata de ingestao |
| Procel-API | PostgreSQL | `parametro_valor` | Valor medido por parametro | `id` | Sem TTL automatico | Ingestao canonica | Analytics/regras | Canonico | Numeric, boolean e texto separados |
| Procel-API | PostgreSQL | `parametro_def` | Definicao de parametro por tipo de sensor | `id` | Sem TTL automatico | Admin/API | Ingestao/regras/analytics | Canonico | Controle de parametros ativos/inativos |
| Procel-API | PostgreSQL | `medicao_ingestao_metadata` | Metadata e idempotencia da ingestao canonica | `id`, constraints unicas | Sem TTL automatico | Ingestao | Auditoria/idempotencia | Canonico/contexto | Preserva contexto bruto da Telemetry |
| Procel-API | PostgreSQL | `sensor_integration_profile` | Perfil de integracao | `id` | Sem TTL automatico | Admin | Parser/worker | Configuracao | Controle de ativacao |
| Procel-API | PostgreSQL | `sensor_integration_parser_version` | Versao de parser | `id` | Sem TTL automatico | Admin | Parser/worker | Configuracao | Apenas versao ativa deve processar |
| Procel-API | PostgreSQL | `sensor_integration_value_mapping` | Mapeamento de valores externos | `id` | Sem TTL automatico | Admin | Parser | Configuracao | Usado por perfil/parser |
| Procel-API | PostgreSQL | `sensor_integration_binding` | Binding perfil-produtor-sensor/source | `id` | Sem TTL automatico | Admin | Snapshot/worker | Configuracao | Evita roteamento ambiguo |
| Procel-API | PostgreSQL | `sensor` | Sensor fisico/logico | `external_id` | Sem TTL automatico | Admin/seed | Ingestao/analytics | Canonico | Pode ter delete logico |
| Procel-API | PostgreSQL | `tipo_de_sensor` | Tipo de sensor | `nome` | Sem TTL automatico | Admin/seed | Sensores/parametros | Canonico | Base para parametros |
| Procel-API | PostgreSQL | `compartimento` | Ambiente fisico | `id` | Sem TTL automatico | Sincronizacao/Admin | Sensores/analytics | Canonico | Relacionado a predio/campus/unidade |
| Procel-API | PostgreSQL | `analytics_aggregation_job` | Solicitacao de agregacao por periodo | `id`, chave idempotente | Sem TTL automatico | Admin/Operador | Worker de agregacao | Operacional | Contem solicitante e progresso |
| Procel-API | PostgreSQL | `analytics_aggregation_window` | Janela de processamento | `id`, `job_id + window_start` | Sem TTL automatico | Job de agregacao | Worker de agregacao | Operacional | Claim/lease/retry por janela |
| Procel-API | PostgreSQL | `analytics_numeric_bucket` | Resultado numerico agregado | `sensor + parametro + inicio + fim + versao` | Sem TTL automatico | Worker de agregacao | Futura API analitica | Analitico | Sem API ampla nesta etapa |
| Procel-API | PostgreSQL | `grupo_regra` | Grupo de regras de qualidade | `id` | Sem TTL automatico | Admin | Avaliacao de medicoes | Canonico | Pode ser associado a sensores |
| Procel-API | PostgreSQL | `regra_parametro` | Regra por parametro | `id` | Sem TTL automatico | Admin | Avaliacao | Canonico | Limites e comparadores |
| Procel-API | PostgreSQL | `avaliacao_parametro_valor` | Resultado de avaliacao | `id` | Sem TTL automatico | API | Consultas operacionais | Canonico | Derivado de parametro valor |
| Procel-API | PostgreSQL | `pessoa`, `pessoa_role` | Usuarios e roles | `id`, role por pessoa | Sem TTL automatico | Admin/bootstrap | Seguranca/Admin | Canonico | Dados pessoais; proteger acesso |
| Procel-API | PostgreSQL | `curso`, `disciplina`, `aluno_disciplina`, `periodo_aula`, `presenca` | Dominio academico | PKs proprias | Sem TTL automatico | Admin/sync | Admin/API | Canonico | `AlunoDisciplina.periodo_letivo` qualifica vinculos; `PeriodoAula` nao armazena periodo letivo; pode conter dados pessoais |
| Procel-API | PostgreSQL | `missao`, `atividade` | Missoes e atividades, incluindo configuracao e instancia de ciclo | `id`, `pessoa_id + missao_id + chave_ciclo` | Sem TTL automatico | Admin/usuario/motor de missoes | Admin/API/motor de missoes | Canonico/operacional | `Missao.ciclo_tipo` default `UNICA`; `Atividade` copia ciclo, progresso necessario e conclusao automatica no momento da criacao |
| Procel-API | PostgreSQL | `atividade_evento` | Vinculo idempotente entre atividade e ocorrencia de evento | `atividade_id + evento_ocorrencia_id + tipo` | Sem TTL automatico | Motor de missoes | Auditoria/progresso/conclusao | Operacional | Nao apagar fisicamente; evita progresso e conclusao duplicados em retry |
| Procel-API | PostgreSQL | `xp_lancamento` | Ledger append-only de XP | `id`, `chave_idempotencia`, uma concessao automatica por atividade | Sem TTL automatico | Motor de missoes | API de saldo/extrato | Auditoria | Saldo de XP e calculado por soma; nao armazenar saldo em `pessoa` |
| Procel-API | PostgreSQL | `evento_definicao`, `evento_condicao` | Catalogo de eventos configuraveis de missoes | `id`, condicao por `evento_definicao_id + ordem` | Sem TTL automatico | Admin/API | Motor de eventos | Configuracao | Condicoes referenciam `ParametroDef` por FK; Drools temporal e opt-in |
| Procel-API | PostgreSQL | `evento_ocorrencia`, `evento_ocorrencia_evidencia`, `evento_avaliacao_request` | Persistencia operacional do motor de eventos | `id`, `chave_idempotencia`, uma request por `medicao_id` | Sem TTL automatico | Motor de eventos | Motor de atividades/Admin | Operacional | Snapshots em JSONB; evidencias nao sao removidas fisicamente; transicoes usam papeis `ANTES`/`DEPOIS` |
| Procel-API | PostgreSQL | `evento_janela_avaliacao`, `evento_janela_evidencia` | Janelas temporais persistentes de missoes | `id`, `chave_idempotencia` | Sem TTL automatico | Motor temporal | Worker temporal/Admin | Operacional | Claim atomico, lease, retry, recuperacao apos reinicio e evidencias por medicao/parametro |

## Fundacao de atividades ciclicas

- `V22__cyclic_mission_activities.sql` adiciona `ciclo_tipo`, `progresso_necessario` e `conclusao_automatica` em `missao`.
- Atividades historicas sao migradas para `chave_ciclo = 'UNICA'`, `ciclo_tipo = 'UNICA'`; atividades `CONCLUIDA` recebem `progresso_atual = progresso_necessario`, demais recebem `0`.
- A unicidade passa de `pessoa_id + missao_id` para `pessoa_id + missao_id + chave_ciclo`, permitindo multiplas execucoes da mesma missao em ciclos diferentes.
- A hierarquia manual existente permanece segura para `UNICA`. Ciclos recorrentes ainda nao replicam arvores de missoes filhas automaticamente.
- Beneficiarios academicos suportados nesta etapa: `ALUNOS_VINCULADOS`, `ATIVADOR_DA_MISSAO` e `SEM_ATRIBUICAO_AUTOMATICA`. `ALUNOS_VINCULADOS_COM_OCUPACAO` e `CHECKIN_CONFIRMADO` sao explicitamente nao suportadas enquanto nao houver confirmacao de ocupacao/check-in.
- Ocorrencias confirmadas podem aplicar progresso idempotente via `atividade_evento`. Ocorrencias temporais so aplicam atividades/XP quando `procel.missions.evaluation.temporal-windows.activities-enabled=true`.
- XP automatico e concedido somente para conclusao automatica de atividade por ocorrencia confirmada, com lancamento append-only em `xp_lancamento`.

## Motor de eventos de missoes

- `V20` cria `evento_definicao` e `evento_condicao` para regras configuraveis.
- `V21` cria `evento_avaliacao_request`, `evento_ocorrencia` e `evento_ocorrencia_evidencia`.
- `V24` cria `evento_janela_avaliacao` e `evento_janela_evidencia` para `DURACAO`, `TRANSICAO` e `JANELA_ENCERRADA`.
- `V25` amplia papeis de evidencia de ocorrencia com `ANTES` e `DEPOIS` para transicoes.
- Workers permanecem desabilitados por padrao; Drools e exclusivo do fluxo temporal opt-in.
