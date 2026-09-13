package com.procel.api.service.missions.cycles;

import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.Missao;
import com.procel.api.entity.missions.MissaoCicloTipo;
import com.procel.api.entity.people.Pessoa;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;

@Component
public class MissionCycleKeyFactory {

    public MissionCycleKey create(
            Missao missao,
            Pessoa pessoa,
            EventoOcorrencia ocorrencia,
            Instant referenceTime,
            ZoneId academicZone
    ) {
        if (missao == null) throw new IllegalArgumentException("missao is required");
        if (pessoa == null) throw new IllegalArgumentException("pessoa is required");
        if (referenceTime == null) throw new IllegalArgumentException("referenceTime is required");
        if (academicZone == null) throw new IllegalArgumentException("academicZone is required");

        MissaoCicloTipo tipo = missao.getCicloTipo() == null ? MissaoCicloTipo.UNICA : missao.getCicloTipo();
        ZonedDateTime zoned = referenceTime.atZone(academicZone);
        return switch (tipo) {
            case UNICA -> new MissionCycleKey("UNICA", tipo, null, null);
            case POR_AULA -> aulaKey(ocorrencia, tipo);
            case POR_PRESENCA -> throw new UnsupportedOperationException("POR_PRESENCA cycle is not supported in this stage");
            case DIARIA -> dailyKey(zoned, tipo);
            case SEMANAL -> weeklyKey(zoned, tipo);
            case MENSAL -> monthlyKey(zoned, tipo);
            case JANELA_PERSONALIZADA -> throw new UnsupportedOperationException("JANELA_PERSONALIZADA cycle is not supported in this stage");
        };
    }

    private static MissionCycleKey aulaKey(EventoOcorrencia ocorrencia, MissaoCicloTipo tipo) {
        if (ocorrencia == null || ocorrencia.getPeriodoAula() == null || ocorrencia.getPeriodoAula().getId() == null) {
            throw new IllegalArgumentException("POR_AULA cycle requires periodoAulaId");
        }
        return new MissionCycleKey(
                "AULA:" + ocorrencia.getPeriodoAula().getId(),
                tipo,
                ocorrencia.getInicioEm(),
                ocorrencia.getFimEm()
        );
    }

    private static MissionCycleKey dailyKey(ZonedDateTime zoned, MissaoCicloTipo tipo) {
        LocalDate date = zoned.toLocalDate();
        ZonedDateTime start = date.atStartOfDay(zoned.getZone());
        return new MissionCycleKey("DIA:" + date, tipo, start.toInstant(), start.plusDays(1).toInstant());
    }

    private static MissionCycleKey weeklyKey(ZonedDateTime zoned, MissaoCicloTipo tipo) {
        WeekFields iso = WeekFields.ISO;
        LocalDate date = zoned.toLocalDate();
        int year = date.get(iso.weekBasedYear());
        int week = date.get(iso.weekOfWeekBasedYear());
        LocalDate startDate = date.with(iso.dayOfWeek(), 1);
        ZonedDateTime start = startDate.atStartOfDay(zoned.getZone());
        return new MissionCycleKey(
                "SEMANA:%04d-W%02d".formatted(year, week),
                tipo,
                start.toInstant(),
                start.plusWeeks(1).toInstant()
        );
    }

    private static MissionCycleKey monthlyKey(ZonedDateTime zoned, MissaoCicloTipo tipo) {
        LocalDate firstDay = zoned.toLocalDate().withDayOfMonth(1);
        ZonedDateTime start = firstDay.atStartOfDay(zoned.getZone());
        return new MissionCycleKey(
                "MES:%04d-%02d".formatted(firstDay.getYear(), firstDay.getMonthValue()),
                tipo,
                start.toInstant(),
                start.plusMonths(1).toInstant()
        );
    }
}
