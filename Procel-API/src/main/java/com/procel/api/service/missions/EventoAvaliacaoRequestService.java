package com.procel.api.service.missions;

import com.procel.api.entity.missions.EventoAvaliacaoRequest;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.EventoAvaliacaoRequestRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventoAvaliacaoRequestService {
    private static final String REQUEST_MEDICAO_CONSTRAINT = "ux_evento_avaliacao_request_medicao";

    private final EventoAvaliacaoRequestRepository requestRepo;
    private final MedicaoRepository medicaoRepo;

    public EventoAvaliacaoRequestService(
            EventoAvaliacaoRequestRepository requestRepo,
            MedicaoRepository medicaoRepo
    ) {
        this.requestRepo = requestRepo;
        this.medicaoRepo = medicaoRepo;
    }

    @Transactional
    public EventoAvaliacaoRequest criarPendente(UUID medicaoId) {
        if (medicaoId == null) throw new IllegalArgumentException("medicaoId is required");
        return requestRepo.findByMedicaoId(medicaoId)
                .orElseGet(() -> createNew(medicaoId));
    }

    @Transactional(readOnly = true)
    public EventoAvaliacaoRequest buscar(UUID id) {
        return requestRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("EventoAvaliacaoRequest not found id=" + id));
    }

    private EventoAvaliacaoRequest createNew(UUID medicaoId) {
        var medicao = medicaoRepo.findById(medicaoId)
                .orElseThrow(() -> new NotFoundException("Medicao not found id=" + medicaoId));
        try {
            return requestRepo.saveAndFlush(new EventoAvaliacaoRequest(medicao, Instant.now()));
        } catch (DataIntegrityViolationException ex) {
            if (!REQUEST_MEDICAO_CONSTRAINT.equals(constraintName(ex))) {
                throw ex;
            }
            return requestRepo.findByMedicaoId(medicaoId).orElseThrow(() -> ex);
        }
    }

    private String constraintName(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            try {
                Object serverError = current.getClass().getMethod("getServerErrorMessage").invoke(current);
                if (serverError == null) continue;
                Object constraint = serverError.getClass().getMethod("getConstraint").invoke(serverError);
                if (constraint != null) return constraint.toString();
            } catch (ReflectiveOperationException ignored) {
                // Keep walking the cause chain.
            }
        }
        return null;
    }
}
