# Decisao tecnica: Drools na avaliacao de missoes

## Problema

O PROCEL precisa decidir se vale evoluir a prova de conceito do Drools para uma integracao de producao no fluxo de missoes. O objetivo tecnico avaliado foi suportar regras configuraveis e cenarios temporais com evidencias, relogio controlado, limites operacionais e isolamento entre avaliacoes, sem conectar o Drools ao worker e sem trocar o `SimpleMissionRuleEngine` como padrao.

## Contexto

- `SimpleMissionRuleEngine` permanece a implementacao padrao.
- `DroolsMissionRuleEngine` continua opt-in por `procel.missions.rule-engine=drools`.
- O worker de missoes nao chama Drools nesta etapa.
- Nao ha DRL fornecido por usuario; as regras sao geradas a partir de `EventoDefinicao` e `EventoCondicao`.
- Nao ha acesso a repositories, entidades JPA, persistencia, atividades ou XP dentro do engine.

## Dependencias e custo

Dependencias diretas adicionadas ao Procel-API:

- `org.kie:kie-api:10.2.0`
- `org.kie:kie-internal:10.2.0`
- `org.drools:drools-engine:10.2.0`
- `org.drools:drools-mvel:10.2.0`

Dependencias de benchmark ficam apenas no profile `drools-benchmark`:

- `org.openjdk.jmh:jmh-core:1.37`
- `org.openjdk.jmh:jmh-generator-annprocess:1.37`

O custo operacional principal nao e a avaliacao quente, mas a compilacao fria do `KieBase` e a alocacao por sessao/avaliacao.

## Cache e limites

O cache atual da POC armazena somente `KieBase`, nunca `KieSession`.

- Limite padrao: `200` entradas.
- Expiracao padrao por inatividade: `30m`.
- `KieSession` nova por avaliacao.
- Compilacoes concorrentes do mesmo fingerprint usam single-flight: uma thread compila, as demais aguardam o mesmo resultado.
- Compilacao com falha nao entra no cache.
- Fingerprint inclui definicao, condicoes e configuracoes que alteram a semantica temporal.

Limites padrao:

- `max-facts-per-evaluation`: `5000`
- `compilation-timeout`: `10s`
- `evaluation-timeout`: `5s`
- `maximum-sample-gap`: `5m`
- `maximum-evaluation-span`: `24h`

## Observabilidade

Metricas adicionadas:

- `procel.missions.drools.compilations`
- `procel.missions.drools.compilation.duration`
- `procel.missions.drools.compilation.failures`
- `procel.missions.drools.cache.hits`
- `procel.missions.drools.cache.misses`
- `procel.missions.drools.cache.evictions`
- `procel.missions.drools.evaluations`
- `procel.missions.drools.evaluation.duration`
- `procel.missions.drools.evaluation.failures`
- `procel.missions.drools.facts`
- `procel.missions.drools.limit.rejections`

Tags usadas:

- `mode`: `INSTANTANEO` ou `DURACAO`
- `result`: `matched`, `unmatched` ou `failed`
- `limit`: conjunto finito para rejeicoes

Nao sao usados IDs de evento, medicao, sala, sensor ou pessoa como tags.

## Benchmark

Comando usado:

```powershell
.\Procel-API\mvnw.cmd -f Procel-API/pom.xml -Pdrools-benchmark -DskipTests test-compile exec:java "-Dexec.args=com.procel.api.benchmark.DroolsMissionRuleEngineBenchmark -wi 0 -i 1 -r 200ms -f 0 -prof gc -rf csv -rff Procel-API/target/drools-jmh-results.csv"
```

Ambiente:

- Windows 11.
- JVM reportada pelo JMH: OpenJDK 21.0.10.
- O runner foi executado in-process (`-f 0`) porque o modo forkado via `exec:java` nao recebeu corretamente `org.openjdk.jmh.runner.ForkedMain`.
- WMI local para CPU/memoria foi bloqueado pelo ambiente; por isso, o hardware nao foi identificado automaticamente.
- Esta medicao e suficiente para direcao de POC, mas nao substitui benchmark de producao com fork, warmup maior e ambiente dedicado.

Resultados principais:

| Cenario | Fatos | Threads | Throughput ops/ms | Media ms | p50 ms | p95 ms | p99 ms | KB/op aprox. |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Simple instantaneo | 10 | 1 | 317.524 | 0.001 | 0.001 | 0.001 | 0.002 | 2.4 |
| Simple instantaneo | 100 | 1 | 356.578 | 0.003 | 0.002 | 0.004 | 0.013 | 7.9 |
| Simple instantaneo | 1000 | 1 | 71.282 | 0.014 | 0.011 | 0.026 | 0.043 | 57.5 |
| Drools instantaneo frio | 10 | 1 | 0.015 | 59.687 | 60.391 | 63.767 | 63.767 | 7241.1 |
| Drools instantaneo frio | 100 | 1 | 0.011 | 58.360 | 58.884 | 61.604 | 61.604 | 7381.8 |
| Drools instantaneo frio | 1000 | 1 | 0.010 | 62.947 | 63.504 | 64.094 | 64.094 | 8273.1 |
| Drools instantaneo quente | 10 | 1 | 1.132 | 0.605 | 0.519 | 1.191 | 1.555 | 87.7 |
| Drools instantaneo quente | 100 | 1 | 1.052 | 0.852 | 0.614 | 1.281 | 6.052 | 132.9 |
| Drools instantaneo quente | 1000 | 1 | 0.632 | 1.556 | 1.370 | 2.575 | 3.892 | 543.6 |
| Drools duracao | 100 | 1 | 0.502 | 0.808 | 0.679 | 1.488 | 2.014 | 206.7 |
| Drools duracao | 1000 | 1 | 0.376 | 2.301 | 2.101 | 3.414 | 5.079 | 1292.8 |
| Drools duracao | 5000 | 1 | 0.108 | 7.366 | 6.898 | 10.478 | 10.666 | 5401.3 |
| Drools concorrente quente | 10 | 1 | 0.915 | 0.914 | 0.775 | 1.533 | 2.224 | 95.6 |
| Drools concorrente quente | 10 | 4 | 2.510 | 1.904 | 1.333 | 4.186 | 12.689 | 86.6 |
| Drools concorrente quente | 10 | 8 | 1.081 | 1.613 | 1.337 | 3.102 | 4.874 | 77.8 |
| Drools concorrente quente | 1000 | 1 | 0.477 | 1.688 | 1.420 | 2.649 | 6.940 | 548.0 |
| Drools concorrente quente | 1000 | 4 | 1.433 | 2.382 | 2.085 | 4.370 | 6.499 | 511.4 |
| Drools concorrente quente | 1000 | 8 | 1.309 | 4.300 | 4.012 | 7.819 | 9.158 | 510.1 |

## Leitura dos resultados

- O Simple segue varias ordens de grandeza mais rapido para regras instantaneas simples.
- Drools frio custa cerca de `58-63 ms` por compilacao e aloca aproximadamente `7-8 MB/op`.
- Cache quente reduz o custo de Drools instantaneo para cerca de `0.6-1.6 ms`, com crescimento de alocacao conforme quantidade de fatos.
- Duracao com `5000` fatos ficou em torno de `7.4 ms` media e `10.7 ms` p99 nesta medicao.
- Concorrencia mostra alguma escalabilidade ate `4` threads, mas `8` threads aumenta variabilidade.
- O wrapper de timeout por avaliacao adiciona overhead relevante, pois a avaliacao e isolada em executor para permitir cancelamento.

## Beneficios para CEP

- Regras temporais ficam mais expressivas que no engine simples.
- Pseudo clock e stream mode dao uma base adequada para evoluir CEP historico.
- `KieBase` cacheado permite amortizar compilacao.
- A separacao entre regra e persistencia reduz risco de efeitos colaterais.

## Riscos operacionais

- Dependencias Drools/KIE aumentam significativamente o grafo de runtime.
- Compilacao fria e cara e precisa ser pre-aquecida ou amortizada por cache.
- O modelo atual de timeout protege o caller, mas custa overhead por avaliacao.
- O benchmark in-process nao e suficiente para SLO final.
- O modo temporal ainda e POC; nao ha janelas persistidas nem integracao com worker.
- Alertas de encerramento de threads apareceram no JMH in-process; isso deve ser reavaliado em benchmark forkado antes de producao.

## Criterios para conexao futura ao worker

Conectar ao worker somente se todos os criterios forem atendidos:

- Benchmark forkado e dedicado com p95/p99 dentro do SLO definido para volume real.
- Pre-aquecimento ou cache hit ratio esperado acima de 95% para eventos ativos.
- Metricas Drools visiveis no ambiente de operacao.
- Regras temporais com semantica aprovada por dominio e testes de regressao.
- Plano para reduzir ou aceitar o overhead do timeout por avaliacao.
- Cenario de falha de compilacao validado como falha permanente segura.
- Nenhum DRL arbitrario fornecido por usuario ou Admin.

## Decisao recomendada

Manter experimental. Nao adotar Drools como engine de producao nem conecta-lo ao worker ainda.

Justificativa:

- A POC mostra que Drools e tecnicamente viavel para CEP e duracao quando o cache esta quente.
- Para regras instantaneas simples, o custo contra `SimpleMissionRuleEngine` e alto demais.
- O custo de compilacao fria e a alocacao ainda exigem governanca operacional.
- O benchmark ainda precisa ser executado em modo forkado e ambiente controlado antes de uma decisao final de producao.

Recomendacao objetiva: evoluir Drools apenas para cenarios temporais/CEP que o engine simples nao cobre bem, mantendo `SimpleMissionRuleEngine` como padrao e sem conectar ao worker ate os criterios acima serem cumpridos.
