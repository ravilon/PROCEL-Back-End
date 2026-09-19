package com.procel.api.service.missions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.procel.api.entity.missions.EventoOcorrencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidencia;
import com.procel.api.entity.missions.EventoOcorrenciaEvidenciaPapel;
import com.procel.api.entity.missions.EventoOcorrenciaStatus;
import com.procel.api.entity.sensors.Medicao;
import com.procel.api.entity.sensors.ParametroValor;
import com.procel.api.exception.ConflictException;
import com.procel.api.exception.NotFoundException;
import com.procel.api.repository.missions.EventoDefinicaoRepository;
import com.procel.api.repository.missions.EventoOcorrenciaEvidenciaRepository;
import com.procel.api.repository.missions.EventoOcorrenciaRepository;
import com.procel.api.repository.rooms.CompartimentoRepository;
import com.procel.api.repository.rooms.PeriodoAulaRepository;
import com.procel.api.repository.sensors.MedicaoRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventoOcorrenciaService {
    private static final String EVIDENCE_VALUE_CONSTRAINT = "ux_evento_evidencia_parametro_valor";
    private static final String EVIDENCE_MEASUREMENT_CONSTRAINT = "ux_evento_evidencia_medicao_sem_parametro";

    private final EventoOcorrenciaRepository ocorrenciaRepo;
    private final EventoOcorrenciaEvidenciaRepository evidenciaRepo;
    private final EventoDefinicaoRepository eventoDefRepo;
    private final CompartimentoRepository compartimentoRepo;
    private final PeriodoAulaRepository periodoAulaRepo;
    private final SensorRepository sensorRepo;
    private final MedicaoRepository medicaoRepo;
    private final ParametroValorRepository parametroValorRepo;
    private final EventoSnapshotFingerprintService fingerprintService;
    private final JdbcTemplate jdbcTemplate;

    public EventoOcorrenciaService(
            EventoOcorrenciaRepository ocorrenciaRepo,
            EventoOcorrenciaEvidenciaRepository evidenciaRepo,
            EventoDefinicaoRepository eventoDefRepo,
            CompartimentoRepository compartimentoRepo,
            PeriodoAulaRepository periodoAulaRepo,
            SensorRepository sensorRepo,
            MedicaoRepository medicaoRepo,
            ParametroValorRepository parametroValorRepo,
            EventoSnapshotFingerprintService fingerprintService,
            JdbcTemplate jdbcTemplate
    ) {
        this.ocorrenciaRepo = ocorrenciaRepo;
        this.evidenciaRepo = evidenciaRepo;
        this.eventoDefRepo = eventoDefRepo;
        this.compartimentoRepo = compartimentoRepo;
        this.periodoAulaRepo = periodoAulaRepo;
        this.sensorRepo = sensorRepo;
        this.medicaoRepo = medicaoRepo;
        this.parametroValorRepo = parametroValorRepo;
        this.fingerprintService = fingerprintService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public EventoOcorrencia registrarOcorrencia(RegistrarOcorrenciaCommand command) {
        validate(command);
        String canonicalSnapshot = fingerprintService.canonicalJson(command.contextoSnapshot());
        String fingerprint = fingerprint(command, canonicalSnapshot);
        Optional<EventoOcorrencia> existing = ocorrenciaRepo.findByChaveIdempotencia(command.chaveIdempotencia());
        if (existing.isPresent()) {
            return equivalentOrConflict(existing.get(), fingerprint);
        }

        ensureReferences(command);
        UUID insertedId = insertOccurrence(command, canonicalSnapshot, fingerprint);
        EventoOcorrencia occurrence = insertedId == null
                ? ocorrenciaRepo.findByChaveIdempotencia(command.chaveIdempotencia())
                        .orElseThrow(() -> new ConflictException("Idempotent occurrence was not found after conflict"))
                : ocorrenciaRepo.findById(insertedId)
                        .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + insertedId));
        return equivalentOrConflict(occurrence, fingerprint);
    }

    @Transactional
    public EventoOcorrenciaEvidencia anexarEvidencia(AnexarEvidenciaCommand command) {
        EventoOcorrencia occurrence = ocorrenciaRepo.findById(command.eventoOcorrenciaId())
                .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + command.eventoOcorrenciaId()));
        Medicao medicao = medicaoRepo.findById(command.medicaoId())
                .orElseThrow(() -> new NotFoundException("Medicao not found id=" + command.medicaoId()));
        ParametroValor parametroValor = null;
        if (command.parametroValorId() != null) {
            parametroValor = parametroValorRepo.findById(command.parametroValorId())
                    .orElseThrow(() -> new NotFoundException("ParametroValor not found id=" + command.parametroValorId()));
            if (!parametroValor.getMedicao().getId().equals(medicao.getId())) {
                throw new ConflictException("ParametroValor does not belong to Medicao id=" + command.medicaoId());
            }
        }
        Optional<EventoOcorrenciaEvidencia> existing = existingEvidence(command);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return evidenciaRepo.saveAndFlush(new EventoOcorrenciaEvidencia(
                    occurrence,
                    medicao,
                    parametroValor,
                    command.papel()
            ));
        } catch (DataIntegrityViolationException ex) {
            String constraint = constraintName(ex);
            if (!EVIDENCE_VALUE_CONSTRAINT.equals(constraint) && !EVIDENCE_MEASUREMENT_CONSTRAINT.equals(constraint)) {
                throw ex;
            }
            return existingEvidence(command).orElseThrow(() -> ex);
        }
    }

    @Transactional
    public void vincularAvaliacao(UUID evidenciaId, UUID parametroValorId, UUID avaliacaoId) {
        int updated = jdbcTemplate.update("""
                update evento_ocorrencia_evidencia e
                set avaliacao_parametro_valor_id = ?
                where e.id = ? and e.parametro_valor_id = ?
                  and (e.avaliacao_parametro_valor_id is null or e.avaliacao_parametro_valor_id = ?)
                  and exists (select 1 from avaliacao_parametro_valor a
                              where a.id = ? and a.parametro_valor_id = ?)
                """, avaliacaoId, evidenciaId, parametroValorId, avaliacaoId, avaliacaoId, parametroValorId);
        if (updated != 1) throw new ConflictException("Rule evaluation does not match evidence");
    }

    @Transactional
    public EventoOcorrencia atualizarStatus(UUID ocorrenciaId, EventoOcorrenciaStatus novoStatus) {
        EventoOcorrencia occurrence = ocorrenciaRepo.findById(ocorrenciaId)
                .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + ocorrenciaId));
        if (occurrence.getStatus() == novoStatus) {
            return occurrence;
        }
        if (!validTransition(occurrence.getStatus(), novoStatus)) {
            throw new ConflictException("Invalid event occurrence status transition "
                    + occurrence.getStatus() + " -> " + novoStatus);
        }
        occurrence.updateStatus(novoStatus);
        return occurrence;
    }

    @Transactional(readOnly = true)
    public EventoOcorrencia buscar(UUID id) {
        return ocorrenciaRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("EventoOcorrencia not found id=" + id));
    }

    @Transactional(readOnly = true)
    public Optional<EventoOcorrencia> buscarPorChaveIdempotencia(String chaveIdempotencia) {
        if (chaveIdempotencia == null || chaveIdempotencia.isBlank()) {
            return Optional.empty();
        }
        return ocorrenciaRepo.findByChaveIdempotencia(chaveIdempotencia);
    }

    private Optional<EventoOcorrenciaEvidencia> existingEvidence(AnexarEvidenciaCommand command) {
        if (command.parametroValorId() == null) {
            return evidenciaRepo.findByEventoOcorrenciaIdAndMedicaoIdAndParametroValorIsNullAndPapel(
                    command.eventoOcorrenciaId(),
                    command.medicaoId(),
                    command.papel()
            );
        }
        return evidenciaRepo.findByEventoOcorrenciaIdAndMedicaoIdAndParametroValorIdAndPapel(
                command.eventoOcorrenciaId(),
                command.medicaoId(),
                command.parametroValorId(),
                command.papel()
        );
    }

    private EventoOcorrencia equivalentOrConflict(EventoOcorrencia existing, String incomingFingerprint) {
        if (Objects.equals(existing.getConteudoFingerprint(), incomingFingerprint)) {
            return existing;
        }
        throw new ConflictException("Idempotency key already used with different event occurrence content");
    }

    private void ensureReferences(RegistrarOcorrenciaCommand command) {
        if (!eventoDefRepo.existsById(command.eventoDefinicaoId())) {
            throw new NotFoundException("EventoDefinicao not found id=" + command.eventoDefinicaoId());
        }
        if (!compartimentoRepo.existsById(command.compartimentoId())) {
            throw new NotFoundException("Compartimento not found id=" + command.compartimentoId());
        }
        if (command.periodoAulaId() != null && !periodoAulaRepo.existsById(command.periodoAulaId())) {
            throw new NotFoundException("PeriodoAula not found id=" + command.periodoAulaId());
        }
        if (command.sensorExternalId() != null && !sensorRepo.existsById(command.sensorExternalId())) {
            throw new NotFoundException("Sensor not found id=" + command.sensorExternalId());
        }
    }

    private UUID insertOccurrence(
            RegistrarOcorrenciaCommand command,
            String canonicalSnapshot,
            String fingerprint
    ) {
        var ids = jdbcTemplate.query("""
                insert into evento_ocorrencia
                (evento_definicao_id, compartimento_id, periodo_aula_id, sensor_external_id, status,
                 inicio_em, fim_em, detectado_em, chave_idempotencia, contexto_snapshot,
                 conteudo_fingerprint, created_at, updated_at)
                values (?, ?, ?, ?, 'DETECTADO', ?, ?, ?, ?, ?::jsonb, ?, now(), now())
                on conflict (chave_idempotencia) do nothing
                returning id
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                command.eventoDefinicaoId(),
                command.compartimentoId(),
                command.periodoAulaId(),
                command.sensorExternalId(),
                Timestamp.from(command.inicioEm()),
                command.fimEm() == null ? null : Timestamp.from(command.fimEm()),
                Timestamp.from(command.detectadoEm()),
                command.chaveIdempotencia(),
                canonicalSnapshot,
                fingerprint
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String fingerprint(RegistrarOcorrenciaCommand command, String canonicalSnapshot) {
        ObjectNode node = fingerprintService.objectNode();
        node.put("eventoDefinicaoId", command.eventoDefinicaoId().toString());
        node.put("compartimentoId", command.compartimentoId());
        putNullable(node, "periodoAulaId", command.periodoAulaId());
        putNullable(node, "sensorExternalId", command.sensorExternalId());
        node.put("inicioEm", command.inicioEm().toString());
        if (command.fimEm() == null) node.putNull("fimEm"); else node.put("fimEm", command.fimEm().toString());
        node.put("detectadoEm", command.detectadoEm().toString());
        node.put("contextoSnapshot", canonicalSnapshot);
        return fingerprintService.fingerprint(node);
    }

    private static void putNullable(ObjectNode node, String field, Object value) {
        if (value == null) node.putNull(field); else node.put(field, value.toString());
    }

    private static void validate(RegistrarOcorrenciaCommand command) {
        if (command == null) throw new IllegalArgumentException("command is required");
        if (command.eventoDefinicaoId() == null) throw new IllegalArgumentException("eventoDefinicaoId is required");
        if (command.compartimentoId() == null || command.compartimentoId().isBlank()) throw new IllegalArgumentException("compartimentoId is required");
        if (command.inicioEm() == null) throw new IllegalArgumentException("inicioEm is required");
        if (command.detectadoEm() == null) throw new IllegalArgumentException("detectadoEm is required");
        if (command.chaveIdempotencia() == null || command.chaveIdempotencia().isBlank()) throw new IllegalArgumentException("chaveIdempotencia is required");
        if (command.contextoSnapshot() == null || command.contextoSnapshot().isBlank()) throw new IllegalArgumentException("contextoSnapshot is required");
        if (command.fimEm() != null && command.inicioEm().isAfter(command.fimEm())) {
            throw new IllegalArgumentException("inicioEm cannot be after fimEm");
        }
    }

    private static boolean validTransition(EventoOcorrenciaStatus from, EventoOcorrenciaStatus to) {
        return switch (from) {
            case DETECTADO -> to == EventoOcorrenciaStatus.CONFIRMADO || to == EventoOcorrenciaStatus.INVALIDADO;
            case CONFIRMADO -> to == EventoOcorrenciaStatus.PROCESSADO || to == EventoOcorrenciaStatus.INVALIDADO;
            case PROCESSADO, INVALIDADO -> false;
        };
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

    public record RegistrarOcorrenciaCommand(
            UUID eventoDefinicaoId,
            String compartimentoId,
            UUID periodoAulaId,
            String sensorExternalId,
            Instant inicioEm,
            Instant fimEm,
            Instant detectadoEm,
            String chaveIdempotencia,
            String contextoSnapshot
    ) {}

    public record AnexarEvidenciaCommand(
            UUID eventoOcorrenciaId,
            UUID medicaoId,
            UUID parametroValorId,
            EventoOcorrenciaEvidenciaPapel papel
    ) {
        public AnexarEvidenciaCommand {
            if (eventoOcorrenciaId == null) throw new IllegalArgumentException("eventoOcorrenciaId is required");
            if (medicaoId == null) throw new IllegalArgumentException("medicaoId is required");
            if (papel == null) throw new IllegalArgumentException("papel is required");
        }
    }
}
