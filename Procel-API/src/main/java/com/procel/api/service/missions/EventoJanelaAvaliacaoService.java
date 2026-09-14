package com.procel.api.service.missions;

import com.procel.api.config.MissionEvaluationProperties;
import com.procel.api.entity.missions.EventoJanelaAvaliacao;
import com.procel.api.entity.missions.EventoJanelaAvaliacaoStatus;
import com.procel.api.entity.missions.EventoJanelaEvidencia;
import com.procel.api.entity.missions.EventoJanelaEvidenciaPapel;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.observability.ApiObservabilityMetrics;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.missions.EventoJanelaAvaliacaoRepository;
import com.procel.api.repository.missions.EventoJanelaEvidenciaRepository;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.rooms.PeriodoAulaRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class EventoJanelaAvaliacaoService {
    private static final String WINDOW_KEY_CONSTRAINT = "ux_evento_janela_chave_idempotencia";
    private static final List<String> FINAL_STATUSES = List.of("SATISFEITA", "INVALIDADA", "EXPIRADA", "FAILED");

    private final EventoJanelaAvaliacaoRepository janelaRepo;
    private final EventoJanelaEvidenciaRepository evidenciaRepo;
    private final EventoDefinicaoRepository eventoRepo;
    private final CompartimentoRepository compartimentoRepo;
    private final PeriodoAulaRepository periodoAulaRepo;
    private final MedicaoRepository medicaoRepo;
    private final ParametroValorRepository parametroValorRepo;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MissionEvaluationProperties properties;
    private final ApiObservabilityMetrics metrics;

    public EventoJanelaAvaliacaoService(
            EventoJanelaAvaliacaoRepository janelaRepo,
            EventoJanelaEvidenciaRepository evidenciaRepo,
            EventoDefinicaoRepository eventoRepo,
            CompartimentoRepository compartimentoRepo,
            PeriodoAulaRepository periodoAulaRepo,
            MedicaoRepository medicaoRepo,
            ParametroValorRepository parametroValorRepo,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            MissionEvaluationProperties properties,
            ApiObservabilityMetrics metrics
    ) {
        this.janelaRepo = janelaRepo;
        this.evidenciaRepo = evidenciaRepo;
        this.eventoRepo = eventoRepo;
        this.compartimentoRepo = compartimentoRepo;
        this.periodoAulaRepo = periodoAulaRepo;
        this.medicaoRepo = medicaoRepo;
        this.parametroValorRepo = parametroValorRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional
    public EventoJanelaAvaliacao abrir(AbrirJanelaCommand command) {
        command.validate(properties.getTemporalWindows().getMaximumWindowDuration());
        String key = command.chaveIdempotencia() == null || command.chaveIdempotencia().isBlank()
                ? chaveDeterministica(command)
                : command.chaveIdempotencia();
        return janelaRepo.findByChaveIdempotencia(key)
                .map(existing -> validateEquivalent(existing, command, key))
                .orElseGet(() -> createNew(command, key));
    }

    @Transactional(readOnly = true)
    public EventoJanelaAvaliacao buscar(UUID id) {
        return janelaRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("EventoJanelaAvaliacao not found id=" + id));
    }

    public List<EventoJanelaWork> claimAvailable(int batchSize, Duration leaseDuration, int maxAttempts) {
        int limit = Math.max(1, batchSize);
        long leaseSeconds = Math.max(1, leaseDuration.toSeconds());
        return transactionTemplate.execute(status -> jdbcTemplate.query("""
                update evento_janela_avaliacao janela
                set status = 'PROCESSING',
                    attempts = janela.attempts + 1,
                    lease_until = now() + (? * interval '1 second'),
                    last_error = null,
                    updated_at = now()
                where janela.id in (
                    select candidate.id
                    from evento_janela_avaliacao candidate
                    where candidate.attempts < ?
                      and (
                            (
                                candidate.status = 'ABERTA'
                                and candidate.proxima_avaliacao_em <= now()
                            )
                            or (
                                candidate.status = 'PROCESSING'
                                and candidate.lease_until <= now()
                            )
                        )
                    order by candidate.proxima_avaliacao_em asc, candidate.created_at asc
                    for update of candidate skip locked
                    limit ?
                )
                returning janela.id, janela.evento_definicao_id, janela.compartimento_id, janela.attempts
                """, (rs, rowNum) -> new EventoJanelaWork(
                        rs.getObject("id", UUID.class),
                        rs.getObject("evento_definicao_id", UUID.class),
                        rs.getString("compartimento_id"),
                        rs.getInt("attempts")
                ), leaseSeconds, maxAttempts, limit));
    }

    public boolean atualizarMedicao(UUID janelaId, Instant medicaoEm, Instant proximaAvaliacaoEm) {
        requireInstant(medicaoEm, "medicaoEm");
        requireInstant(proximaAvaliacaoEm, "proximaAvaliacaoEm");
        int updated = jdbcTemplate.update("""
                update evento_janela_avaliacao
                set ultima_medicao_em = greatest(coalesce(ultima_medicao_em, ?), ?),
                    proxima_avaliacao_em = ?,
                    status = case when status = 'PROCESSING' then 'ABERTA' else status end,
                    lease_until = null,
                    updated_at = now()
                where id = ?
                  and status not in ('SATISFEITA','INVALIDADA','EXPIRADA','FAILED')
                  and ? >= inicio_em
                  and ? < fim_previsto_em
                """, Timestamp.from(medicaoEm), Timestamp.from(medicaoEm),
                Timestamp.from(proximaAvaliacaoEm), janelaId, Timestamp.from(medicaoEm), Timestamp.from(medicaoEm));
        return updated == 1;
    }

    public boolean satisfazer(UUID janelaId) {
        int updated = transitionToFinal(janelaId, EventoJanelaAvaliacaoStatus.SATISFEITA, null);
        if (updated == 1) metrics.missionTemporalWindowSatisfied();
        return updated == 1;
    }

    public boolean invalidar(UUID janelaId, String reason) {
        return transitionToFinal(janelaId, EventoJanelaAvaliacaoStatus.INVALIDADA, reason) == 1;
    }

    public boolean expirar(UUID janelaId, String reason) {
        int updated = transitionToFinal(janelaId, EventoJanelaAvaliacaoStatus.EXPIRADA, reason);
        if (updated == 1) metrics.missionTemporalWindowExpired();
        return updated == 1;
    }

    public boolean marcarRetry(UUID janelaId, Instant proximaAvaliacaoEm, String reason) {
        int updated = jdbcTemplate.update("""
                update evento_janela_avaliacao
                set status = 'ABERTA',
                    proxima_avaliacao_em = ?,
                    lease_until = null,
                    last_error = ?,
                    updated_at = now()
                where id = ?
                  and status = 'PROCESSING'
                """, Timestamp.from(proximaAvaliacaoEm), trimError(reason), janelaId);
        if (updated == 1) metrics.missionTemporalWindowRetry();
        return updated == 1;
    }

    public boolean marcarFailed(UUID janelaId, String reason) {
        int updated = transitionToFinal(janelaId, EventoJanelaAvaliacaoStatus.FAILED, reason);
        if (updated == 1) metrics.missionTemporalWindowFailed();
        return updated == 1;
    }

    public int expirarJanelasVencidas(Instant now) {
        return transactionTemplate.execute(status -> {
            int byGap = jdbcTemplate.update("""
                    update evento_janela_avaliacao
                    set status = 'EXPIRADA',
                        lease_until = null,
                        last_error = coalesce(last_error, 'Maximum sample gap exceeded'),
                        updated_at = now()
                    where status in ('ABERTA','PROCESSING')
                      and ? < fim_previsto_em
                      and ultima_medicao_em is not null
                      and ultima_medicao_em + (? * interval '1 second') < ?
                    """, Timestamp.from(now), properties.getTemporalWindows().getMaximumSampleGap().toSeconds(), Timestamp.from(now));
            for (int i = 0; i < byGap; i++) metrics.missionTemporalWindowExpired();
            return byGap;
        });
    }

    public boolean recuperarLeaseExpirado(UUID janelaId, Instant proximaAvaliacaoEm, String reason) {
        int updated = jdbcTemplate.update("""
                update evento_janela_avaliacao
                set status = 'ABERTA',
                    lease_until = null,
                    proxima_avaliacao_em = ?,
                    last_error = ?,
                    updated_at = now()
                where id = ?
                  and status = 'PROCESSING'
                  and lease_until <= now()
                  and attempts < ?
                """, Timestamp.from(proximaAvaliacaoEm), trimError(reason), janelaId,
                properties.getTemporalWindows().getMaxAttempts());
        if (updated == 1) metrics.missionTemporalWindowRetry();
        return updated == 1;
    }

    public long countBacklog() {
        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from evento_janela_avaliacao
                where status in ('ABERTA','PROCESSING')
                """, Long.class);
        return count == null ? 0 : count;
    }

    @Transactional
    public EventoJanelaEvidencia anexarEvidencia(AnexarEvidenciaCommand command) {
        var janela = buscar(command.janelaId());
        var medicao = medicaoRepo.findById(command.medicaoId())
                .orElseThrow(() -> new NotFoundException("Medicao not found id=" + command.medicaoId()));
        var parametroValor = command.parametroValorId() == null ? null : parametroValorRepo.findById(command.parametroValorId())
                .orElseThrow(() -> new NotFoundException("ParametroValor not found id=" + command.parametroValorId()));
        if (parametroValor != null && !parametroValor.getMedicao().getId().equals(command.medicaoId())) {
            throw new ConflictException("ParametroValor does not belong to medicao");
        }
        var existing = parametroValor == null
                ? evidenciaRepo.findByJanelaAvaliacaoIdAndMedicaoIdAndParametroValorIsNullAndPapel(
                command.janelaId(), command.medicaoId(), command.papel())
                : evidenciaRepo.findByJanelaAvaliacaoIdAndMedicaoIdAndParametroValorIdAndPapel(
                command.janelaId(), command.medicaoId(), command.parametroValorId(), command.papel());
        return existing.orElseGet(() -> evidenciaRepo.saveAndFlush(
                new EventoJanelaEvidencia(janela, medicao, parametroValor, command.papel())
        ));
    }

    private EventoJanelaAvaliacao createNew(AbrirJanelaCommand command, String key) {
        long backlog = countBacklog();
        if (backlog >= properties.getTemporalWindows().getMaxBacklog()) {
            throw new ConflictException("Temporal window backlog limit reached");
        }
        var evento = eventoRepo.findById(command.eventoDefinicaoId())
                .orElseThrow(() -> new NotFoundException("EventoDefinicao not found id=" + command.eventoDefinicaoId()));
        var compartimento = compartimentoRepo.findById(command.compartimentoId())
                .orElseThrow(() -> new NotFoundException("Compartimento not found id=" + command.compartimentoId()));
        var periodoAula = command.periodoAulaId() == null ? null : periodoAulaRepo.findById(command.periodoAulaId())
                .orElseThrow(() -> new NotFoundException("PeriodoAula not found id=" + command.periodoAulaId()));
        try {
            var created = janelaRepo.saveAndFlush(new EventoJanelaAvaliacao(
                    evento,
                    compartimento,
                    periodoAula,
                    command.inicioEm(),
                    command.fimPrevistoEm(),
                    command.ultimaMedicaoEm(),
                    command.proximaAvaliacaoEm(),
                    key,
                    command.contextoSnapshot()
            ));
            metrics.missionTemporalWindowOpened();
            return created;
        } catch (DataIntegrityViolationException ex) {
            if (!WINDOW_KEY_CONSTRAINT.equals(constraintName(ex))) throw ex;
            return janelaRepo.findByChaveIdempotencia(key)
                    .map(existing -> validateEquivalent(existing, command, key))
                    .orElseThrow(() -> ex);
        }
    }

    private EventoJanelaAvaliacao validateEquivalent(EventoJanelaAvaliacao existing, AbrirJanelaCommand command, String key) {
        boolean equivalent = existing.getEventoDefinicao().getId().equals(command.eventoDefinicaoId())
                && existing.getCompartimento().getId().equals(command.compartimentoId())
                && sameUuid(existing.getPeriodoAula() == null ? null : existing.getPeriodoAula().getId(), command.periodoAulaId())
                && existing.getInicioEm().equals(command.inicioEm())
                && existing.getFimPrevistoEm().equals(command.fimPrevistoEm());
        if (!equivalent) {
            throw new ConflictException("EventoJanelaAvaliacao key conflict chaveIdempotencia=" + key);
        }
        return existing;
    }

    private int transitionToFinal(UUID janelaId, EventoJanelaAvaliacaoStatus status, String reason) {
        if (!FINAL_STATUSES.contains(status.name())) {
            throw new IllegalArgumentException("status must be final");
        }
        return jdbcTemplate.update("""
                update evento_janela_avaliacao
                set status = ?,
                    lease_until = null,
                    last_error = ?,
                    updated_at = now()
                where id = ?
                  and status not in ('SATISFEITA','INVALIDADA','EXPIRADA','FAILED')
                """, status.name(), trimError(reason), janelaId);
    }

    private static String chaveDeterministica(AbrirJanelaCommand command) {
        String raw = "temporal-window:%s:%s:%s:%s:%s".formatted(
                command.eventoDefinicaoId(),
                command.compartimentoId(),
                command.periodoAulaId() == null ? "sem-aula" : command.periodoAulaId(),
                command.inicioEm(),
                command.fimPrevistoEm()
        );
        return raw.length() <= 200 ? raw : "temporal-window:" + sha256(raw);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean sameUuid(UUID a, UUID b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void requireInstant(Instant value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }

    private static String trimError(String error) {
        if (error == null || error.isBlank()) return null;
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

    public record AbrirJanelaCommand(
            UUID eventoDefinicaoId,
            String compartimentoId,
            UUID periodoAulaId,
            Instant inicioEm,
            Instant fimPrevistoEm,
            Instant ultimaMedicaoEm,
            Instant proximaAvaliacaoEm,
            String chaveIdempotencia,
            String contextoSnapshot
    ) {
        void validate(Duration maximumWindowDuration) {
            if (eventoDefinicaoId == null) throw new IllegalArgumentException("eventoDefinicaoId is required");
            if (compartimentoId == null || compartimentoId.isBlank()) throw new IllegalArgumentException("compartimentoId is required");
            requireInstant(inicioEm, "inicioEm");
            requireInstant(fimPrevistoEm, "fimPrevistoEm");
            requireInstant(proximaAvaliacaoEm, "proximaAvaliacaoEm");
            if (!inicioEm.isBefore(fimPrevistoEm)) throw new IllegalArgumentException("window interval must be semi-open and non-empty");
            if (Duration.between(inicioEm, fimPrevistoEm).compareTo(maximumWindowDuration) > 0) {
                throw new IllegalArgumentException("window exceeds maximum duration");
            }
            if (ultimaMedicaoEm != null && (ultimaMedicaoEm.isBefore(inicioEm) || !ultimaMedicaoEm.isBefore(fimPrevistoEm))) {
                throw new IllegalArgumentException("ultimaMedicaoEm must be within [inicioEm, fimPrevistoEm)");
            }
            if (contextoSnapshot == null || contextoSnapshot.isBlank()) throw new IllegalArgumentException("contextoSnapshot is required");
        }
    }

    public record AnexarEvidenciaCommand(
            UUID janelaId,
            UUID medicaoId,
            UUID parametroValorId,
            EventoJanelaEvidenciaPapel papel
    ) {
        public AnexarEvidenciaCommand {
            if (janelaId == null) throw new IllegalArgumentException("janelaId is required");
            if (medicaoId == null) throw new IllegalArgumentException("medicaoId is required");
            if (papel == null) throw new IllegalArgumentException("papel is required");
        }
    }

    public record EventoJanelaWork(
            UUID janelaId,
            UUID eventoDefinicaoId,
            String compartimentoId,
            int attempts
    ) {}
}
