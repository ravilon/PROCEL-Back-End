# Documentos

Documentacao tecnica complementar do PROBEN.

| Caminho | Conteudo |
| --- | --- |
| `ArquiteturaGeral/Arquitetura-PROBEN.drawio` | Diagrama anterior, mantido como historico |
| `ArquiteturaGeral/Arquitetura-PROBEN-atual.drawio` | Arquitetura atual de componentes e fluxos |
| `DER-BancoAnalitico/DER-Salas.drawio` | DER parcial; migrations Flyway definem o schema completo |
| `Modelo-MongoDB/` | Modelo documental da telemetria bruta |
| `MQTT.md` | Contrato e operacao MQTT |
| `Seguranca-e-Operacao.md` | Seguranca, secrets, CORS e operacao |
| `Catalogo-Dados.md` | Catalogo de tabelas e collections |
| `Decisao-Drools-POC.md` | Decisao tecnica e benchmark do Drools opt-in |
| `MissionEventWindowClosedSemantics.md` | Semantica de eventos `JANELA_ENCERRADA` |
| `Staging.md` | Provisionamento e validacao segura de staging |
| `Organizacao-do-Codigo.md` | Estrutura dos modulos e avaliacao de organizacao |

`Arquitetura-PROBEN-atual.drawio` e a referencia atual; o diagrama anterior e mantido como historico. `DER-Salas.drawio` conserva a pagina original e inclui uma segunda pagina expandida com entidades e relacoes das migrations V29-V33, usando a mesma notacao visual. `Proben-Telemetry` nao acessa diretamente PostgreSQL. Migrations Flyway em `Proben-API/src/main/resources/db/migration` definem o schema completo.
