package com.proben.api.service.rooms;

import java.time.LocalDate;
import java.util.List;

/**
 * Componente de aulas source usado pelo modulo API.
 */

public interface AulasSource {
    List<AulaRecord> fetchAulas(String compartimentoId, LocalDate weekStart);
}
