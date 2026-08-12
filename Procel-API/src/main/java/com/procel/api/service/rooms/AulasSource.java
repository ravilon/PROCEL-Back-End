package com.procel.api.service.rooms;

import java.time.LocalDate;
import java.util.List;

public interface AulasSource {
    List<AulaRecord> fetchAulas(String compartimentoId, LocalDate weekStart);
}
