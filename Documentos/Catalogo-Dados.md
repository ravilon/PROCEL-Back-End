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
| Procel-API | PostgreSQL | `curso`, `disciplina`, `aluno_disciplina`, `ocorrencia_aula`, `presenca` | Dominio academico | PKs proprias | Sem TTL automatico | Admin/sync | Admin/API | Canonico | Pode conter dados pessoais |
| Procel-API | PostgreSQL | `missao`, `atividade` | Missoes e atividades | `id` | Sem TTL automatico | Admin/usuario | Admin/API | Canonico | Dados operacionais |
