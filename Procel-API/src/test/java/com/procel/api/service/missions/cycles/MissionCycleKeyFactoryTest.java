package com.procel.api.service.missions.cycles;

import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.missions.MissaoCicloTipo;
import com.procel.api.entity.people.Pessoa;
import com.procel.api.entity.rooms.PeriodoAula;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionCycleKeyFactoryTest {

    private final MissionCycleKeyFactory factory = new MissionCycleKeyFactory();
    private final Pessoa pessoa = mock(Pessoa.class);
    private final ZoneId zone = ZoneId.of("America/Sao_Paulo");

    @Test
    void createsUniqueKey() {
        assertThat(key(MissaoCicloTipo.UNICA, Instant.parse("2026-09-13T10:00:00Z")).chave())
                .isEqualTo("UNICA");
    }

    @Test
    void createsClassKeyAndRejectsMissingClass() {
        UUID periodoAulaId = UUID.randomUUID();
        PeriodoAula periodoAula = mock(PeriodoAula.class);
        EventoOcorrencia ocorrencia = mock(EventoOcorrencia.class);
        when(periodoAula.getId()).thenReturn(periodoAulaId);
        when(ocorrencia.getPeriodoAula()).thenReturn(periodoAula);
        when(ocorrencia.getInicioEm()).thenReturn(Instant.parse("2026-09-13T10:00:00Z"));
        when(ocorrencia.getFimEm()).thenReturn(Instant.parse("2026-09-13T10:50:00Z"));

        var key = factory.create(mission(MissaoCicloTipo.POR_AULA), pessoa, ocorrencia,
                Instant.parse("2026-09-13T10:10:00Z"), zone);

        assertThat(key.chave()).isEqualTo("AULA:" + periodoAulaId);
        assertThat(key.inicio()).isEqualTo(Instant.parse("2026-09-13T10:00:00Z"));
        assertThatThrownBy(() -> factory.create(mission(MissaoCicloTipo.POR_AULA), pessoa, null,
                Instant.parse("2026-09-13T10:10:00Z"), zone))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodoAulaId");
    }

    @Test
    void createsDailyKeyUsingAcademicTimezone() {
        var key = key(MissaoCicloTipo.DIARIA, Instant.parse("2026-09-13T02:30:00Z"));

        assertThat(key.chave()).isEqualTo("DIA:2026-09-12");
        assertThat(key.inicio()).isEqualTo(Instant.parse("2026-09-12T03:00:00Z"));
        assertThat(key.fim()).isEqualTo(Instant.parse("2026-09-13T03:00:00Z"));
    }

    @Test
    void createsIsoWeeklyKeyAcrossYearBoundary() {
        var key = key(MissaoCicloTipo.SEMANAL, Instant.parse("2026-12-31T12:00:00Z"));

        assertThat(key.chave()).isEqualTo("SEMANA:2026-W53");
    }

    @Test
    void createsMonthlyKey() {
        var key = key(MissaoCicloTipo.MENSAL, Instant.parse("2026-09-13T10:00:00Z"));

        assertThat(key.chave()).isEqualTo("MES:2026-09");
        assertThat(key.inicio()).isEqualTo(Instant.parse("2026-09-01T03:00:00Z"));
    }

    @Test
    void unsupportedCyclesFailExplicitly() {
        assertThatThrownBy(() -> key(MissaoCicloTipo.POR_PRESENCA, Instant.parse("2026-09-13T10:00:00Z")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("POR_PRESENCA");
        assertThatThrownBy(() -> key(MissaoCicloTipo.JANELA_PERSONALIZADA, Instant.parse("2026-09-13T10:00:00Z")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("JANELA_PERSONALIZADA");
    }

    private MissionCycleKey key(MissaoCicloTipo tipo, Instant instant) {
        return factory.create(mission(tipo), pessoa, null, instant, zone);
    }

    private static Missao mission(MissaoCicloTipo tipo) {
        Missao missao = mock(Missao.class);
        when(missao.getCicloTipo()).thenReturn(tipo);
        return missao;
    }
}
