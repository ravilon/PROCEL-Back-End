package com.procel.ingestion.service.sensors;

import com.procel.ingestion.dto.sensors.MedicaoDTOs;
import com.procel.ingestion.entity.sensors.Medicao;
import com.procel.ingestion.entity.sensors.ParametroValor;
import com.procel.ingestion.repository.sensors.MedicaoRepository;
import com.procel.ingestion.repository.sensors.MedicaoSpecs;
import com.procel.ingestion.repository.sensors.ParametroValorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MedicoesQueryService {

    private final MedicaoRepository medicaoRepo;
    private final ParametroValorRepository valorRepo;

    public MedicoesQueryService(MedicaoRepository medicaoRepo, ParametroValorRepository valorRepo) {
        this.medicaoRepo = medicaoRepo;
        this.valorRepo = valorRepo;
    }

    // =========================
    // SENSOR
    // =========================

    @Transactional(readOnly = true)
    public List<MedicaoDTOs.MedicaoResponse> listarPorSensor(
            String sensorExternalId,
            Instant from,
            Instant to,
            int limit
    ) {
        int safeLimit = clampLimit(limit);

        Specification<Medicao> spec = Specification.where(MedicaoSpecs.bySensor(sensorExternalId));
        if (from != null) spec = spec.and(MedicaoSpecs.from(from));
        if (to != null)   spec = spec.and(MedicaoSpecs.to(to));

        var pageable = PageRequest.of(
                0,
                safeLimit,
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        List<Medicao> list = medicaoRepo.findAll(spec, pageable).getContent();
        return enrich(list);
    }

    @Transactional(readOnly = true)
    public Optional<MedicaoDTOs.MedicaoResponse> latestPorSensor(String sensorExternalId) {

        Specification<Medicao> spec = Specification.where(MedicaoSpecs.bySensor(sensorExternalId));

        var pageable = PageRequest.of(
                0,
                1,
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        List<Medicao> list = medicaoRepo.findAll(spec, pageable).getContent();
        if (list.isEmpty()) return Optional.empty();

        return Optional.of(enrichOne(list.get(0)));
    }

    // =========================
    // COMPARTIMENTO
    // =========================

    @Transactional(readOnly = true)
    public List<MedicaoDTOs.MedicaoResponse> listarPorCompartimento(
            String compartimentoId,
            Instant from,
            Instant to,
            int limit
    ) {
        int safeLimit = clampLimit(limit);

        Specification<Medicao> spec = Specification.where(MedicaoSpecs.byCompartimento(compartimentoId));
        if (from != null) spec = spec.and(MedicaoSpecs.from(from));
        if (to != null)   spec = spec.and(MedicaoSpecs.to(to));

        var pageable = PageRequest.of(
                0,
                safeLimit,
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        List<Medicao> medicoes = medicaoRepo.findAll(spec, pageable).getContent();
        return enrich(medicoes);
    }

    @Transactional(readOnly = true)
    public Optional<MedicaoDTOs.MedicaoResponse> latestPorCompartimento(String compartimentoId) {

        Specification<Medicao> spec = Specification.where(MedicaoSpecs.byCompartimento(compartimentoId));

        var pageable = PageRequest.of(
                0,
                1,
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        List<Medicao> list = medicaoRepo.findAll(spec, pageable).getContent();
        if (list.isEmpty()) return Optional.empty();

        return Optional.of(enrichOne(list.get(0)));
    }

    // =========================
    // ENRICH (parametros)
    // =========================

    private List<MedicaoDTOs.MedicaoResponse> enrich(List<Medicao> medicoes) {
        if (medicoes.isEmpty()) return List.of();

        List<UUID> ids = medicoes.stream().map(Medicao::getId).toList();
        List<ParametroValor> valores = valorRepo.findAllByMedicao_IdIn(ids);

        Map<UUID, List<ParametroValor>> byMedicao = valores.stream()
                .collect(Collectors.groupingBy(v -> v.getMedicao().getId()));

        return medicoes.stream()
                .map(m -> toResponse(m, byMedicao.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    private MedicaoDTOs.MedicaoResponse enrichOne(Medicao m) {
        List<ParametroValor> vals = valorRepo.findAllByMedicao_IdIn(List.of(m.getId()));
        return toResponse(m, vals);
    }

    private MedicaoDTOs.MedicaoResponse toResponse(Medicao m, List<ParametroValor> valores) {

        Map<String, Object> payload = new LinkedHashMap<>();
        for (ParametroValor pv : valores) {
            String key = pv.getParametroDef().getNome();
            Object value = switch (pv.getParametroDef().getDataType()) {
                case BOOLEAN -> pv.getBooleanValue();
                case TEXT -> pv.getTextValue();
                case NUMERIC -> pv.getNumericValue();
            };
            payload.put(key, value);
        }

        var sensor = m.getSensor();
        var tipo = sensor.getTipo();
        var comp = sensor.getCompartimento();

        return new MedicaoDTOs.MedicaoResponse(
                m.getId(),
                sensor.getExternalId(),
                (tipo != null ? tipo.getNome() : null),
                (comp != null ? comp.getId() : null),
                m.getTimestamp(),
                m.getRecebidoEm(),
                m.getSource(),
                payload
        );
    }

    private int clampLimit(int limit) {
        if (limit <= 0) return 200;
        return Math.min(limit, 1000);
    }
}