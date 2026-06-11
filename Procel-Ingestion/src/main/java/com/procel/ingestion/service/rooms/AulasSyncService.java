package com.procel.ingestion.service.rooms;

import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class AulasSyncService {

    private static final Logger log = LoggerFactory.getLogger(AulasSyncService.class);
    private static final int MAX_REPORTED_ERRORS = 20;
    private static final List<String> CLASSROOM_TYPES = List.of(
            "Sala de Aula",
            "Laboratório de Uso Livre",
            "Laboratório de Ensino",
            "Laboratório de Pesquisa",
            "Auditório"
    );

    private final AulasSource source;
    private final AulasIngestionService ingestionService;
    private final CompartimentoRepository compartimentoRepository;

    public AulasSyncService(
            AulasSource source,
            AulasIngestionService ingestionService,
            CompartimentoRepository compartimentoRepository
    ) {
        this.source = source;
        this.ingestionService = ingestionService;
        this.compartimentoRepository = compartimentoRepository;
    }

    public AulasSyncResult syncAll(LocalDate date) {
        long start = System.currentTimeMillis();
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        List<String> roomIds = resolveRoomIds();

        int roomsSynced = 0;
        int roomsFailed = 0;
        int occurrencesFetched = 0;
        int occurrencesDeleted = 0;
        int occurrencesInserted = 0;
        int disciplinesCreated = 0;
        int disciplinesUpdated = 0;
        List<String> errors = new ArrayList<>();

        for (String roomId : roomIds) {
            try {
                List<AulaRecord> records = source.fetchAulas(roomId, weekStart);
                occurrencesFetched += records.size();

                AulasRoomIngestionResult result = ingestionService.replaceWeek(
                        roomId,
                        weekStart,
                        records
                );

                occurrencesDeleted += result.deleted();
                occurrencesInserted += result.inserted();
                disciplinesCreated += result.disciplinesCreated();
                disciplinesUpdated += result.disciplinesUpdated();
                roomsSynced++;
            } catch (Exception ex) {
                roomsFailed++;
                log.error(
                        "Class schedule sync failed | compartimentoId={} weekStart={}",
                        roomId,
                        weekStart,
                        ex
                );

                if (errors.size() < MAX_REPORTED_ERRORS) {
                    errors.add("room=" + roomId + ": " + rootMessage(ex));
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info(
                "Class schedule sync finished | weekStart={} roomsRequested={} roomsSynced={} " +
                        "roomsFailed={} fetched={} inserted={} deleted={} elapsedMs={}",
                weekStart,
                roomIds.size(),
                roomsSynced,
                roomsFailed,
                occurrencesFetched,
                occurrencesInserted,
                occurrencesDeleted,
                elapsed
        );

        return new AulasSyncResult(
                weekStart,
                weekEnd,
                roomIds.size(),
                roomsSynced,
                roomsFailed,
                occurrencesFetched,
                occurrencesDeleted,
                occurrencesInserted,
                disciplinesCreated,
                disciplinesUpdated,
                elapsed,
                List.copyOf(errors)
        );
    }

    private List<String> resolveRoomIds() {
        return compartimentoRepository.findByTipoIn(CLASSROOM_TYPES).stream()
                .map(Compartimento::getId)
                .sorted()
                .toList();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null
                ? current.getMessage()
                : current.getClass().getSimpleName();
    }
}
