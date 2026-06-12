package com.procel.ingestion.integration.cobalto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.entity.rooms.PeriodoAulaTipo;
import com.procel.ingestion.service.rooms.AulaRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CobaltoAulasSourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDisciplineFromWeeklyGridCell() throws Exception {
        CobaltoAulasSource source = source();
        List<AulaRecord> output = new ArrayList<>();

        source.parseRow(
                objectMapper.readTree("""
                        {
                          "periodo": 1,
                          "periodo_descritivo": "1 - 08:00 / 08:50",
                          "terca": "<p><a href=\\"https://institucional.ufpel.edu.br/disciplinas/id/27064\\">(CDTEC) T1 - SISTEMAS DISCRETOS</a>"
                        }
                        """),
                LocalDate.of(2026, 6, 14),
                1,
                output
        );

        assertThat(output).containsExactly(new AulaRecord(
                27064L,
                "SISTEMAS DISCRETOS",
                "CDTEC",
                LocalDate.of(2026, 6, 16),
                1,
                1,
                LocalTime.of(8, 0),
                LocalTime.of(8, 50),
                "T1",
                PeriodoAulaTipo.AULA,
                "(CDTEC) T1 - SISTEMAS DISCRETOS"
        ));
    }

    @Test
    void parsesReservationWithoutDiscipline() throws Exception {
        CobaltoAulasSource source = source();
        List<AulaRecord> output = new ArrayList<>();

        source.parseRow(
                objectMapper.readTree("""
                        {
                          "periodo": 3,
                          "periodo_descritivo": "3 - 10:00 / 10:50",
                          "segunda": "<p><a href=\\"#\\">(CDTEC) Sala para aplicar prova de Semântica formal</a>"
                        }
                        """),
                LocalDate.of(2026, 6, 14),
                1,
                output
        );

        assertThat(output).hasSize(1);
        AulaRecord record = output.getFirst();
        assertThat(record.disciplinaId()).isNull();
        assertThat(record.data()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(record.tipo()).isEqualTo(PeriodoAulaTipo.PROVA);
        assertThat(record.descricao()).contains("Sala para aplicar prova");
    }

    @Test
    void includesCobaltoSortFieldInScheduleRequest() {
        String url = source().buildScheduleUrl(
                "1000",
                LocalDate.of(2026, 6, 7),
                1
        );

        assertThat(url)
                .contains("compartimentoId=1000")
                .contains("turno=1")
                .contains("sidx=periodo")
                .contains("sord=ASC");
    }

    private CobaltoAulasSource source() {
        CobaltoProperties properties = new CobaltoProperties();
        properties.setScheduleUrl("https://example.test/schedule");
        return new CobaltoAulasSource(
                org.springframework.web.client.RestClient.builder(),
                objectMapper,
                properties
        );
    }
}
