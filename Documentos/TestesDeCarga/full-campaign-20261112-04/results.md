# Campanha TCC 20261112-04

- Ambiente: staging isolado (https://procel.servehttp.com)
- Período: 2026-11-12T21:46:44.1731367+00:00 a 2026-11-12T21:59:03.8629131+00:00
- 10 salas/sensores virtuais; 20 estudantes; quatro modos, três repetições por modo.
- Carga alvo: 60 mensagens por repetição a 2 msg/s; total esperado 720.
- Cada mensagem tem 13 parâmetros; valores de sala sintéticos, sem alegação de calibração física.
- ACK do publisher mede publicação/ack do broker; atraso canônico é medido separadamente por timestamp de origem e recebimento.
- Temperaturas externas fixas de contexto WeatherAPI: média 7 dias 18,23 °C; observação 25,6 °C.

## Resumo estatístico por modo

| Modo | n | ACK | Mensagens/s (média ± DP) | ACK médio ms (média ± DP) | ACK p95 ms (média ± DP) | Cânonicas/run | Avaliações/run | Alertas/run | Ocorrências/run | Janelas/run | Atividades concluídas/run | Pontos/run |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| duracao | 3 | 100.0% | 2.03 ± 0.00 | 37.12 ± 1.08 | 44.90 ± 5.12 | 60.00 ± 0.00 | 780.00 ± 0.00 | 6.00 ± 0.00 | 3.00 ± 2.65 | 21.67 ± 2.89 | 13.33 ± 11.55 | 66.67 ± 57.74 |
| instantaneo | 3 | 100.0% | 2.03 ± 0.00 | 41.96 ± 2.44 | 64.31 ± 15.42 | 60.00 ± 0.00 | 780.00 ± 0.00 | 6.00 ± 0.00 | 29.33 ± 1.15 | 0.00 ± 0.00 | 20.00 ± 0.00 | 100.00 ± 0.00 |
| janela-encerrada | 3 | 100.0% | 2.03 ± 0.00 | 38.24 ± 0.64 | 48.23 ± 3.11 | 60.00 ± 0.00 | 780.00 ± 0.00 | 6.00 ± 0.00 | 23.33 ± 4.16 | 23.33 ± 4.16 | 20.00 ± 0.00 | 100.00 ± 0.00 |
| transicao | 3 | 100.0% | 2.03 ± 0.00 | 39.66 ± 1.19 | 51.23 ± 2.07 | 60.00 ± 0.00 | 780.00 ± 0.00 | 6.00 ± 0.00 | 10.00 ± 0.00 | 0.00 ± 0.00 | 20.00 ± 0.00 | 100.00 ± 0.00 |

## Reconciliação

- Publicações aceitas: 720; falhas: 0.
- Medições canônicas: 720/720.
- Valores de parâmetros: 9360/9360.
- Avaliações do grupo de regras: 9360/9360.
- Lançamentos de pontos associados às missões: 220.
- Reconciliação integral de medições/avaliações: sim.

## Observação sobre duração

Na repetição `duracao-r3`, as 25 janelas foram invalidadas com `Duration did not remain satisfied for 10 seconds`; não houve atividade concluída nem lançamento de pontos nessa repetição. As outras duas repetições de duração concluíram 20 atividades cada. Isso reduz o total de lançamentos de pontos da campanha a 220, em vez de 240. A causa operacional não foi confirmada nesta execução; portanto, o modo duração deve ser tratado como resultado inconsistente e requer nova execução diagnóstica antes de inferir comportamento.

A consulta de buckets encontrou 540 registros de agregação para o intervalo e modelo usados na campanha.

## Escopo e limites

A campanha é um ensaio controlado de fluxo completo em taxa sustentada de 2 msg/s e não mede o ponto de saturação ou capacidade máxima. O publisher confirma o broker; persistência, qualificações e efeitos de missão são conferidos por consultas autenticadas à API. Dados ambientais internos, ocupação, energia, ruído e CO₂ são valores sintéticos. Os alertas representam casos fora da faixa de especificação cadastrada, não medições físicas. Os intervalos t com n=3 são exploratórios e devem ser apresentados junto dos valores individuais e do desvio padrão.

## Artefatos

Cada pasta `runs/<runId>` contém o fluxo de eventos NDJSON, manifesto/hash, resumo do publisher, reconciliação canônica e métricas de missões, janelas, atividades e ledger de pontos. `preexisting-active-events.json` guarda os quatro eventos temporariamente desativados e restaurados após os testes. `cleanup.json` registra a desativação das 12 missões temporárias após a coleta, preservando medições, ocorrências, atividades e lançamentos. `setup-attempts.json` documenta duas tentativas de configuração descartadas.
