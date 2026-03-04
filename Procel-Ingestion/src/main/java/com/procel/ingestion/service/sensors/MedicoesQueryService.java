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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MedicoesQueryService {

    private final MedicaoRepository medicaoRepo;
    private final ParametroValorRepository valorRepo;

    public MedicoesQueryService(MedicaoRepository medicaoRepo, ParametroValorRepository valorRepo) {
        this.medicaoRepo = medicaoRepo;
        this.valorRepo = valorRepo;
    }

    @Transactional(readOnly = true)
    @GetMapping("/api/sensors/{sensorExternalId}/medicoes")
    public List<MedicaoDTOs.MedicaoResponse> listarPorSensor(
        @PathVariable String sensorExternalId,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "50") int limit
    ) {
        Instant fromI = parseInstantOrNull(from);
        Instant toI   = parseInstantOrNull(to);

        int safeLimit = clampLimit(limit);
        List<Medicao> list = medicaoRepo.findBySensorExternalId(sensorExternalId, fromI, toI, PageRequest.of(0, safeLimit));

        return enrich(list);
    }

    @Transactional(readOnly = true)
    public List<MedicaoDTOs.MedicaoResponse> listarPorCompartimento(String compartimentoId, Instant from, Instant to, int limit) {
        int safeLimit = clampLimit(limit);
        List<Medicao> medicoes = medicaoRepo.findByCompartimentoId(compartimentoId, from, to, PageRequest.of(0, safeLimit));
        return enrich(medicoes);
    }

    @Transactional(readOnly = true)
    public Optional<MedicaoDTOs.MedicaoResponse> latestPorSensor(String sensorExternalId) {
        List<Medicao> list = medicaoRepo.findLatestBySensorExternalId(sensorExternalId, PageRequest.of(0, 1));
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(enrichOne(list.get(0)));
    }
    
    @Transactional(readOnly = true)
    public Optional<MedicaoDTOs.MedicaoResponse> latestPorCompartimento(String compartimentoId) {
        List<Medicao> list = medicaoRepo.findLatestByCompartimentoId(compartimentoId, PageRequest.of(0, 1));
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(enrichOne(list.get(0)));
    }

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

    private Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}