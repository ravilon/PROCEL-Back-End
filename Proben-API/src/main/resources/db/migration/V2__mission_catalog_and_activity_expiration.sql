alter table if exists atividade
    drop constraint if exists atividade_status_check;

alter table if exists atividade
    add constraint atividade_status_check
    check ((status in ('PENDENTE','EM_ANDAMENTO','CONCLUIDA','EXPIRADA','CANCELADA')));

insert into missao (id, titulo, descricao, tipo, value, ativo, created_at)
select v.id, v.titulo, v.descricao, 'Individual', v.value, true, now()
from (
    values
    ('10000000-0000-0000-0000-000000000001'::uuid, 'Último a Apagar', 'Aluno aciona "fechar sala" no app ao sair; sensores registram ausência (presença=0) e queda da luminosidade para nível mínimo em até 2 min. Dificuldade: Fácil. Recompensa sugerida: 20 XP.', 20),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'Sala Vazia, Luz Off', 'Aluno entra em sala vazia com luz acesa, apaga, e o sistema detecta transição de presença>0 com luz alta para presença>0 com luz baixa e depois presença=0. Dificuldade: Fácil. Recompensa sugerida: 20 XP.', 20),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'Luz na Medida', 'Em uma aula, nível de luminosidade artificial fica dentro da faixa econômica recomendada, sem tudo ligado, enquanto sensor de presença indica sala ocupada. Dificuldade: Médio. Recompensa sugerida: 25 XP.', 25),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'AC Inteligente', 'Aluno ajusta o setpoint do AC para a faixa recomendada, por exemplo 23-25 C, durante a aula; o sistema registra o novo setpoint e manutenção por pelo menos 30 min. Dificuldade: Médio. Recompensa sugerida: 25 XP.', 25),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'Intervalo Fresco Natural', 'Durante o intervalo, janelas/portas estão abertas conforme sensor e AC permanece desligado; há presença, mas sem consumo de refrigeração. Dificuldade: Médio. Recompensa sugerida: 25 XP.', 25),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'Aula com Luz Natural', 'Em uma aula diurna, sensores registram luminosidade ambiente alta e baixo uso de iluminação artificial na maior parte do tempo. Dificuldade: Médio. Recompensa sugerida: 25 XP.', 25),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'Checagem de Corredor', 'Aluno percorre um corredor com 3 salas; após sua passagem, sensores de presença e luz indicam salas vazias com luz apagada. Dificuldade: Médio. Recompensa sugerida: 30 XP.', 30),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'Elevador Zero', 'Aluno marca no app que fará "dia de escada"; contadores de uso de elevador mostram que, no período em que ele esteve em aulas, o elevador não foi usado entre os andares que ele frequenta. Dificuldade: Difícil. Recompensa sugerida: 35 XP.', 35),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'Aula Econômica', 'Em uma aula escolhida, consumo total da sala por hora, medido pelo medidor de energia da sala, fica abaixo do padrão histórico desse horário. Dificuldade: Difícil. Recompensa sugerida: 35 XP.', 35),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'Verificação Inicial', 'Ao chegar na sala, aluno confirma no app que luz e AC estavam desligados; sensores confirmam que havia ausência prolongada e baixo consumo antes da chegada. Dificuldade: Fácil. Recompensa sugerida: 15 XP.', 15),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'Controle de Porta', 'Em sala climatizada, aluno garante porta fechada durante a maior parte da aula, conforme sensor de porta, evitando perda de eficiência do AC. Dificuldade: Fácil. Recompensa sugerida: 20 XP.', 20),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'Turno Eficiente', 'Aluno passa um turno, manhã ou tarde, em aulas; consumo por presença nas salas onde ele esteve fica abaixo da média dessas salas no mesmo turno em semanas anteriores. Dificuldade: Difícil. Recompensa sugerida: 40 XP.', 40),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'Detetive de Sala Quente', 'Aluno marca uma sala como desconfortável; sensores de temperatura confirmam desvio da faixa ideal, ajudando ajustes futuros. Dificuldade: Fácil. Recompensa sugerida: 15 XP.', 15),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'Detetive de Sala Fria', 'Aluno marca uma sala como excessivamente fria; relatório coerente com sensores garante XP por feedback útil. Dificuldade: Fácil. Recompensa sugerida: 15 XP.', 15),
    ('10000000-0000-0000-0000-000000000015'::uuid, 'Relato Coerente', 'Aluno responde mini-questionário de conforto, luz e temperatura, após aula; respostas estão coerentes com leituras dos sensores dentro de faixas esperadas. Dificuldade: Fácil. Recompensa sugerida: 20 XP.', 20),
    ('10000000-0000-0000-0000-000000000016'::uuid, 'Power Hour', 'Aluno ativa missão para 1 aula; nesse período, sala não registra picos anormais de consumo nem luz acesa em ausência. Dificuldade: Difícil. Recompensa sugerida: 40 XP.', 40),
    ('10000000-0000-0000-0000-000000000017'::uuid, 'Turno Sem AC', 'Em dia de clima ameno, aluno escolhe uma aula onde o AC permanece desligado; sensores de temperatura mostram sala em faixa aceitável sem refrigeração ativa. Dificuldade: Difícil. Recompensa sugerida: 40 XP.', 40),
    ('10000000-0000-0000-0000-000000000018'::uuid, 'Monitor de Luz', 'Durante breve período após o término de aula, aluno espera até sensores registrarem luz apagada e ausência; missão reforça rotina correta de saída. Dificuldade: Fácil. Recompensa sugerida: 20 XP.', 20),
    ('10000000-0000-0000-0000-000000000019'::uuid, 'Auditor do Andar', 'Aluno percorre um andar em horário de baixa ocupação; após 10-15 min, sensores indicam nenhuma sala vazia com luz ou AC ligados naquele andar. Dificuldade: Difícil. Recompensa sugerida: 40 XP.', 40),
    ('10000000-0000-0000-0000-000000000020'::uuid, 'Minimizar Pico', 'Em uma aula à noite, consumo máximo da sala fica abaixo do pico típico histórico daquele horário. Dificuldade: Médio. Recompensa sugerida: 30 XP.', 30),
    ('10000000-0000-0000-0000-000000000021'::uuid, 'Sala Eco-estudo', 'Em sala de estudo, aluno ativa missão; período de uso registra consumo estável e sem iluminação/AC ligados após ausência final. Dificuldade: Médio. Recompensa sugerida: 30 XP.', 30),
    ('10000000-0000-0000-0000-000000000022'::uuid, 'Chegada Consciente', 'Aluno entra em sala e liga apenas iluminação necessária; medidor registra aumento de consumo menor que a média usual para início de aula. Dificuldade: Médio. Recompensa sugerida: 30 XP.', 30),
    ('10000000-0000-0000-0000-000000000023'::uuid, 'Mudança Visível', 'Após ação do aluno, ajuste de luz ou AC, gráfico de consumo da sala mostra queda clara comparado aos minutos anteriores. Dificuldade: Médio. Recompensa sugerida: 30 XP.', 30),
    ('10000000-0000-0000-0000-000000000024'::uuid, 'Sem Sobras', 'Ao fim da aula, o tempo entre professor/alunos saindo, conforme sensores de presença, e queda de consumo para modo vazio é menor que X minutos. Aluno que acionou missão recebe o crédito. Dificuldade: Médio. Recompensa sugerida: 30 XP.', 30),
    ('10000000-0000-0000-0000-000000000025'::uuid, 'Múltiplas Salas Eficientes', 'Em um mesmo dia, aluno entra em 3 salas diferentes que, naquele horário, estão abaixo de seu baseline de consumo/presença; app mostra verde. Dificuldade: Difícil. Recompensa sugerida: 40 XP.', 40),
    ('10000000-0000-0000-0000-000000000026'::uuid, 'Semana da Escada', 'Em prédios monitorados, aluno marca "sem elevador" por 3 dias; contagem de uso de elevador entre andares que ele frequenta cai em relação à semana anterior. Dificuldade: Difícil. Recompensa sugerida: 40 XP.', 40),
    ('10000000-0000-0000-0000-000000000027'::uuid, 'Acompanhando o Gráfico', 'Aluno abre o dashboard em sala e acompanha uma ação, luz ou AC; app mostra queda em tempo quase real e missão é concluída ao visualizar a mudança. Dificuldade: Fácil. Recompensa sugerida: 15 XP.', 15),
    ('10000000-0000-0000-0000-000000000028'::uuid, 'Preferência Eficiente', 'Aluno escolhe estudar em uma sala marcada como de alto desempenho energético; essa sala está consistentemente verde no mapa de sensores. Dificuldade: Fácil. Recompensa sugerida: 20 XP.', 20),
    ('10000000-0000-0000-0000-000000000029'::uuid, 'Reporte de Ocupação', 'Aluno sinaliza no app que uma sala está vazia; esse estado se alinha com sensores de presença/luz por certo período, ajudando calibrar o sistema. Dificuldade: Fácil. Recompensa sugerida: 15 XP.', 15),
    ('10000000-0000-0000-0000-000000000030'::uuid, 'Insight Aplicado', 'Aluno sugere ajuste, por exemplo reduzir iluminação de corredor, e após a mudança medidores mostram redução consistente naquele circuito; missão é liberada para quem sugeriu. Dificuldade: Muito difícil. Recompensa sugerida: 60 XP.', 60)
) as v(id, titulo, descricao, value)
where not exists (
    select 1
    from missao m
    where m.id = v.id
       or lower(m.titulo) = lower(v.titulo)
);
