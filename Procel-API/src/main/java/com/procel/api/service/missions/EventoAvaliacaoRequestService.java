package com.procel.api.service.missions;

import com.procel.api.entity.missions.EventoAvaliacaoRequest;
import com.procel.api.entity.missions.EventoAvaliacaoRequestStatus;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.EventoAvaliacaoRequestRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventoAvaliacaoRequestService {
    private static final String REQUEST_MEDICAO_CONSTRAINT = "ux_evento_avaliacao_request_medicao";

    private final EventoAvaliacaoRequestRepository requestRepo;
    private final MedicaoRepository medicaoRepo;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public EventoAvaliacaoRequestService(
            EventoAvaliacaoRequestRepository requestRepo,
            MedicaoRepository medicaoRepo,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate
    ) {
        this.requestRepo = requestRepo;
        this.medicaoRepo = medicaoRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
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

    public List<EventoAvaliacaoWork> claimAvailable(
            int batchSize,
            Duration leaseDuration,
            int maxAttempts
    ) {
        int limit = Math.max(1, batchSize);
        long leaseSeconds = Math.max(1, leaseDuration.toSeconds());
        return transactionTemplate.execute(status -> jdbcTemplate.query("""
                update evento_avaliacao_request request
                set status = 'PROCESSING',
                    attempts = request.attempts + 1,
                    claimed_at = now(),
                    lease_until = now() + (? * interval '1 second'),
                    processed_at = null,
                    last_error = null,
                    updated_at = now()
                where request.id in (
                    select candidate.id
                    from evento_avaliacao_request candidate
                    where candidate.attempts < ?
                      and (
                            (
                                candidate.status in ('PENDING','RETRY')
                                and candidate.available_at <= now()
                            )
                            or (
                                candidate.status = 'PROCESSING'
                                and candidate.lease_until <= now()
                            )
                        )
                    order by candidate.available_at asc, candidate.created_at asc
                    for update of candidate skip locked
                    limit ?
                )
                returning request.id, request.medicao_id, request.attempts
                """, (rs, rowNum) -> new EventoAvaliacaoWork(
                        rs.getObject("id", UUID.class),
                        rs.getObject("medicao_id", UUID.class),
                        rs.getInt("attempts")
                ), leaseSeconds, maxAttempts, limit));
    }

    public void markCompleted(UUID requestId) {
        markTerminal(requestId, EventoAvaliacaoRequestStatus.COMPLETED, null);
    }

    public void markIgnored(UUID requestId, String reason) {
        markTerminal(requestId, EventoAvaliacaoRequestStatus.IGNORED, reason);
    }

    public void markFailed(UUID requestId, String reason) {
        markTerminal(requestId, EventoAvaliacaoRequestStatus.FAILED, reason);
    }

    public void markRetry(UUID requestId, Instant availableAt, String reason) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                update evento_avaliacao_request
                set status = 'RETRY',
                    available_at = ?,
                    claimed_at = null,
                    lease_until = null,
                    processed_at = null,
                    last_error = ?,
                    updated_at = now()
                where id = ?
                """, Timestamp.from(availableAt), trimError(reason), requestId));
    }

    public long countBacklog() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from evento_avaliacao_request
                where status in ('PENDING','RETRY','PROCESSING')
                """, Long.class);
        return count == null ? 0 : count;
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

    private void markTerminal(UUID requestId, EventoAvaliacaoRequestStatus terminalStatus, String reason) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                update evento_avaliacao_request
                set status = ?,
                    claimed_at = null,
                    lease_until = null,
                    processed_at = now(),
                    last_error = ?,
                    updated_at = now()
                where id = ?
                """, terminalStatus.name(), trimError(reason), requestId));
    }

    private static String trimError(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
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

    public record EventoAvaliacaoWork(
            UUID requestId,
            UUID medicaoId,
            int attempts
    ) {}
}
