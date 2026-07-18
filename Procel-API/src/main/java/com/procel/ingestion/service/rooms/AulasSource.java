package com.procel.ingestion.service.rooms;

import java.time.LocalDate;
import java.util.List;

public interface AulasSource {
    List<AulaRecord> fetchAulas(String compartimentoId, LocalDate weekStart);
}
