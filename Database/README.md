# Database

Esta pasta guarda SQL legado e consultas auxiliares. O schema operacional atual do `Proben-API` deve ser derivado de Flyway em:

```text
Proben-API/src/main/resources/db/migration
```

Arquivos existentes:

| Arquivo | Uso |
| --- | --- |
| `PROBEN-API/createAnaliticalDB.sql` | Script legado de criacao inicial |
| `PROBEN-API/migrateMissaoModeloAtividade.sql` | Script legado de migracao de missoes |
| `criaSalas.sql` | Script legado relacionado a salas |
| `proben_consultas_uteis.sql` | Consultas operacionais auxiliares |

Nao use estes arquivos como fonte unica de verdade para o schema atual. As tabelas recentes de integracao, telemetry metadata, agregacao, buckets, motor de missoes, janelas temporais, atividades ciclicas e ledger de pontos estao nas migrations Flyway.

O schema documentado chega atualmente a `V34__rename_xp_ledger_to_points.sql`. As migrations V29-V32 incluem contextos compartilhados, observacoes externas, localizacao e coleta HTTP; V33 inclui regras ambientais; V34 renomeia o ledger e os campos de recompensa para pontos, preservando os dados existentes. Consulte as migrations diretamente para campos, constraints e indices vigentes.
