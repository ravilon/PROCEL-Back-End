# Database

Esta pasta guarda SQL legado e consultas auxiliares. O schema operacional atual do `Procel-API` deve ser derivado de Flyway em:

```text
Procel-API/src/main/resources/db/migration
```

Arquivos existentes:

| Arquivo | Uso |
| --- | --- |
| `PROCEL-API/createAnaliticalDB.sql` | Script legado de criacao inicial |
| `PROCEL-API/migrateMissaoModeloAtividade.sql` | Script legado de migracao de missoes |
| `criaSalas.sql` | Script legado relacionado a salas |
| `procel_consultas_uteis.sql` | Consultas operacionais auxiliares |

Nao use estes arquivos como fonte unica de verdade para o schema atual. As tabelas recentes de integracao, telemetry metadata, agregacao e buckets estao nas migrations Flyway.
