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

### Causa corrigida no benchmark forkado

A primeira versao do profile executava `org.openjdk.jmh.Main` via `exec:java`. O runner principal
encontrava o JMH, mas os processos forkados abertos pelo JMH nao recebiam o classpath de teste
completo; por isso o fork falhava ao carregar `org.openjdk.jmh.runner.ForkedMain`.

A correcao foi manter o profile `drools-benchmark` separado e trocar a execucao para
`exec:exec`, chamando explicitamente `java -cp <classpath de teste> org.openjdk.jmh.Main`.
Assim, as classes geradas pelo annotation processor do JMH, as classes de benchmark e `jmh-core`
ficam visiveis tambem dentro dos forks.

Comando forkado usado:

```powershell
.\Procel-API\mvnw.cmd -f Procel-API/pom.xml -Pdrools-benchmark -DskipTests test-compile exec:exec
```

Ambiente:

- Windows 11.
- JVM reportada pelo JMH: OpenJDK 21.0.10.
- Execucao forkada com `2` forks, `5` warmups, `10` medicoes, `1s` por iteracao e `-prof gc`.
- Resultados exportados para `Procel-API/target/drools-jmh-results.csv`.
- WMI local para CPU/memoria foi bloqueado pelo ambiente; por isso, o hardware nao foi identificado automaticamente.
- Esta medicao e reprodutivel no workspace, mas ainda nao substitui benchmark de producao em ambiente dedicado.

Resultados principais:

| Cenario | Fatos | Threads | Throughput ops/ms | Media ms | p50 ms | p95 ms | p99 ms | KB/op aprox. |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Simple instantaneo | 10 | 1 | 1724.164 | 0.001 | 0.001 | 0.001 | 0.002 | 2.4 |
| Simple instantaneo | 100 | 1 | 537.348 | 0.002 | 0.002 | 0.003 | 0.007 | 7.9 |
| Simple instantaneo | 1000 | 1 | 70.222 | 0.016 | 0.011 | 0.026 | 0.049 | 57.1 |
| Drools instantaneo frio | 10 | 1 | 0.023 | 43.200 | 42.009 | 55.325 | 77.956 | 4860.0 |
| Drools instantaneo frio | 100 | 1 | 0.023 | 44.774 | 43.254 | 58.655 | 72.561 | 4966.9 |
| Drools instantaneo frio | 1000 | 1 | 0.022 | 44.619 | 42.500 | 58.907 | 97.750 | 5622.6 |
| Drools instantaneo quente | 10 | 1 | 1.322 | 0.801 | 0.676 | 1.440 | 2.150 | 69.7 |
| Drools instantaneo quente | 100 | 1 | 1.181 | 0.876 | 0.746 | 1.571 | 2.312 | 106.6 |
| Drools instantaneo quente | 1000 | 1 | 0.644 | 1.502 | 1.331 | 2.359 | 3.011 | 459.4 |
| Drools duracao | 100 | 1 | 1.144 | 0.946 | 0.820 | 1.640 | 2.280 | 185.0 |
| Drools duracao | 1000 | 1 | 0.390 | 2.112 | 1.903 | 3.177 | 3.920 | 1203.6 |
| Drools duracao | 5000 | 1 | 0.145 | 6.907 | 6.541 | 9.224 | 11.026 | 4992.6 |

## Leitura dos resultados

- O Simple segue varias ordens de grandeza mais rapido para regras instantaneas simples.
- Drools frio custa cerca de `43-45 ms` por avaliacao nesta medicao forkada e aloca aproximadamente `4.9-5.6 MB/op`.
- Cache quente reduz o custo de Drools instantaneo para cerca de `0.8-1.5 ms`, com crescimento de alocacao conforme quantidade de fatos.
- Duracao com `5000` fatos ficou em torno de `6.9 ms` media e `11.0 ms` p99 nesta medicao.
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
- O benchmark forkado e suficiente para decisao da POC, mas ainda deve ser repetido em ambiente dedicado antes de SLO final.
- O modo temporal ainda e POC; nao ha janelas persistidas nem integracao com worker.
- O cenario frio e bastante verboso por logs internos do KIE a cada criacao de `KieBase`; convem ajustar logging em execucoes de benchmark longas, sem alterar logs de producao.

## Criterios para conexao futura ao worker

Conectar ao worker somente se todos os criterios forem atendidos:

- Benchmark dedicado, em hardware representativo, com p95/p99 dentro do SLO definido para volume real.
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
- O benchmark forkado confirmou viabilidade tecnica com cache quente, mas o ambiente ainda nao representa producao nem ha SLO operacional aprovado.

Recomendacao objetiva: evoluir Drools apenas para cenarios temporais/CEP que o engine simples nao cobre bem, mantendo `SimpleMissionRuleEngine` como padrao e sem conectar ao worker ate os criterios acima serem cumpridos.
