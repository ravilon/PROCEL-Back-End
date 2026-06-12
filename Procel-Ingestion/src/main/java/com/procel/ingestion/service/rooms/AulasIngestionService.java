package com.procel.ingestion.service.rooms;

import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.entity.rooms.Disciplina;
import com.procel.ingestion.entity.rooms.PeriodoAula;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import com.procel.ingestion.repository.rooms.DisciplinaRepository;
import com.procel.ingestion.repository.rooms.PeriodoAulaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AulasIngestionService {

    private static final String SOURCE = "COBALTO";

    private final CompartimentoRepository compartimentoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final PeriodoAulaRepository periodoAulaRepository;

    public AulasIngestionService(
            CompartimentoRepository compartimentoRepository,
            DisciplinaRepository disciplinaRepository,
            PeriodoAulaRepository periodoAulaRepository
    ) {
        this.compartimentoRepository = compartimentoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.periodoAulaRepository = periodoAulaRepository;
    }

    @Transactional
    public AulasRoomIngestionResult replaceWeek(
            String compartimentoId,
            LocalDate weekStart,
            List<AulaRecord> records
    ) {
        Compartimento compartimento = compartimentoRepository.findById(compartimentoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Compartimento not found id=" + compartimentoId
                ));

        LocalDate weekEnd = weekStart.plusDays(6);
        int deleted = Math.toIntExact(
                periodoAulaRepository.deleteByCompartimentoIdAndDataBetween(
                        compartimentoId,
                        weekStart,
                        weekEnd
                )
        );

        Map<Long, Disciplina> disciplinas = new HashMap<>();
        int disciplinesCreated = 0;
        int disciplinesUpdated = 0;
        List<PeriodoAula> periodosAula = new ArrayList<>(records.size());

        for (AulaRecord record : records) {
            Disciplina disciplina = null;

            if (record.disciplinaId() != null) {
                disciplina = disciplinas.get(record.disciplinaId());

                if (disciplina == null) {
                    disciplina = disciplinaRepository.findById(record.disciplinaId()).orElse(null);

                    if (disciplina == null) {
                        disciplina = disciplinaRepository.save(new Disciplina(
                                record.disciplinaId(),
                                record.disciplinaNome(),
                                record.unidadeSigla()
                        ));
                        disciplinesCreated++;
                    } else if (updateDisciplina(disciplina, record)) {
                        disciplina = disciplinaRepository.save(disciplina);
                        disciplinesUpdated++;
                    }

                    disciplinas.put(record.disciplinaId(), disciplina);
                }
            }

            periodosAula.add(new PeriodoAula(
                    compartimento,
                    disciplina,
                    record.data(),
                    record.turno(),
                    record.periodoAula(),
                    record.horaInicio(),
                    record.horaFim(),
                    record.turma(),
                    record.tipo(),
                    record.descricao(),
                    SOURCE
            ));
        }

        periodoAulaRepository.saveAll(periodosAula);

        return new AulasRoomIngestionResult(
                deleted,
                periodosAula.size(),
                disciplinesCreated,
                disciplinesUpdated
        );
    }

    private boolean updateDisciplina(Disciplina disciplina, AulaRecord record) {
        boolean changed = false;

        if (!equalsNullable(disciplina.getNome(), record.disciplinaNome())) {
            disciplina.setNome(record.disciplinaNome());
            changed = true;
        }
        if (!equalsNullable(disciplina.getUnidadeSigla(), record.unidadeSigla())) {
            disciplina.setUnidadeSigla(record.unidadeSigla());
            changed = true;
        }

        return changed;
    }

    private boolean equalsNullable(Object left, Object right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return left.equals(right);
    }
}
