# Documentos

Documentacao tecnica complementar do PROCEL.

| Caminho | Conteudo |
| --- | --- |
| `ArquiteturaGeral/Arquitetura-PROCEL.drawio` | Arquitetura geral atual |
| `DER-BancoAnalitico/DER-Salas.drawio` | DER existente do banco analitico |
| `Modelo-MongoDB/` | Modelo documental da telemetria bruta |
| `MQTT.md` | Contrato e operacao MQTT |
| `Seguranca-e-Operacao.md` | Seguranca, secrets, CORS e operacao |
| `Catalogo-Dados.md` | Catalogo de tabelas e collections |
| `Decisao-Drools-POC.md` | Decisao tecnica e benchmark do Drools opt-in |
| `MissionEventWindowClosedSemantics.md` | Semantica de eventos `JANELA_ENCERRADA` |
| `Staging.md` | Provisionamento e validacao segura de staging |

Os diagramas distinguem componentes implementados, parciais e planejados. `Procel-Telemetry` nao deve ser representado acessando diretamente o PostgreSQL.
O DER do banco analitico deve acompanhar as migrations Flyway atuais, mantendo o
estilo visual existente.
