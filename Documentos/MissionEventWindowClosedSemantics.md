# JANELA_ENCERRADA

## Semantica

`JANELA_ENCERRADA` e um disparo temporal opt-in para avaliar o estado observado ao final de uma janela persistida. Nesta etapa ele suporta somente `modoAvaliacao = INSTANTANEO`.

A janela e aberta quando uma medicao canonica satisfaz as condicoes obrigatorias do evento. O intervalo persistido e semiaberto: `[inicioEm, fimPrevistoEm)`, onde `fimPrevistoEm = inicioEm + janelaSegundos`.

Enquanto a janela esta aberta, medicoes do mesmo contexto temporal atualizam `ultimaMedicaoEm` e registram evidencias da janela. Uma condicao falsa durante a janela nao invalida automaticamente `JANELA_ENCERRADA`; a decisao acontece no fechamento. Lacunas acima de `maximumSampleGap` continuam expirando a janela pela infraestrutura temporal comum.

No processamento do fechamento, o worker temporal reclama a janela por lease, reconstrui os fatos do PostgreSQL no intervalo semiaberto e avalia o ultimo fato de cada parametro requerido. `ALL` exige que todos os parametros obrigatorios estejam satisfeitos no ultimo fato observado dentro do intervalo; `ANY` exige pelo menos um. Condicoes opcionais sao registradas como evidencia quando ha fato correspondente, mas nao decidem a ocorrencia.

Se a avaliacao confirmar, a ocorrencia usa chave idempotente `event:{eventoId}:window:{janelaId}`, recebe snapshot auditavel e evidencias `CONDICAO` dos fatos usados. Atividades, progresso, conclusao e XP so sao aplicados quando `procel.missions.evaluation.temporal-windows.activities-enabled=true`; com a flag desabilitada, a ocorrencia permanece `CONFIRMADO`.

## Fora desta etapa

- `JANELA_ENCERRADA` com `DURACAO`, `AGREGADO` ou `CONTAGEM`.
- Admin ou DRL configuravel.
- Persistencia de agregacoes derivadas.
- Conexao com deploy ou ambientes externos.
