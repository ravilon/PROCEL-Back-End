package com.procel.ingestion.service.sensors;

import com.procel.ingestion.dto.sensors.RegraDTOs;
import com.procel.ingestion.entity.sensors.*;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.sensors.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RegrasService {

    private final GrupoRegraRepository grupoRepo;
    private final RegraParametroRepository regraRepo;
    private final ParametroDefRepository parametroDefRepo;
    private final SensorRepository sensorRepo;
    private final SensorGrupoRegraRepository sensorGrupoRepo;

    public RegrasService(
            GrupoRegraRepository grupoRepo,
            RegraParametroRepository regraRepo,
            ParametroDefRepository parametroDefRepo,
            SensorRepository sensorRepo,
            SensorGrupoRegraRepository sensorGrupoRepo
    ) {
        this.grupoRepo = grupoRepo;
        this.regraRepo = regraRepo;
        this.parametroDefRepo = parametroDefRepo;
        this.sensorRepo = sensorRepo;
        this.sensorGrupoRepo = sensorGrupoRepo;
    }

    @Transactional
    public RegraDTOs.GrupoRegraResponse criarGrupo(RegraDTOs.GrupoRegraRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.nome() == null || req.nome().isBlank()) throw new IllegalArgumentException("nome is required");

        GrupoRegra grupo = grupoRepo.save(new GrupoRegra(
                req.nome().trim(),
                blankToNull(req.descricao()),
                req.ativo() == null || req.ativo()
        ));
        return toGrupoResponse(grupo);
    }

    @Transactional(readOnly = true)
    public List<RegraDTOs.GrupoRegraResponse> listarGrupos() {
        return grupoRepo.findAll().stream().map(RegrasService::toGrupoResponse).toList();
    }

    @Transactional
    public RegraDTOs.RegraParametroResponse criarRegra(UUID grupoId, RegraDTOs.RegraParametroRequest req) {
        if (grupoId == null) throw new IllegalArgumentException("grupoId is required");
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.parametroDefId() == null) throw new IllegalArgumentException("parametroDefId is required");
        if (req.nome() == null || req.nome().isBlank()) throw new IllegalArgumentException("nome is required");
        if (req.operador() == null) throw new IllegalArgumentException("operador is required");
        if (req.resultado() == null) throw new IllegalArgumentException("resultado is required");

        GrupoRegra grupo = grupoRepo.findById(grupoId)
                .orElseThrow(() -> new NotFoundException("GrupoRegra not found id=" + grupoId));
        ParametroDef parametroDef = parametroDefRepo.findById(req.parametroDefId())
                .orElseThrow(() -> new NotFoundException("ParametroDef not found id=" + req.parametroDefId()));
        validateRegra(parametroDef, req);
        boolean ativo = req.ativo() == null || req.ativo();
        if (ativo && regraRepo.existsByGrupoRegra_IdAndParametroDef_IdAndAtivoTrue(grupoId, req.parametroDefId())) {
            throw new IllegalArgumentException("GrupoRegra already has an active rule for parametroDefId=" + req.parametroDefId());
        }

        RegraParametro regra = new RegraParametro();
        regra.setGrupoRegra(grupo);
        regra.setParametroDef(parametroDef);
        regra.setNome(req.nome().trim());
        regra.setDescricao(blankToNull(req.descricao()));
        regra.setOperador(req.operador());
        regra.setValorNumeric1(req.valorNumeric1());
        regra.setValorNumeric2(req.valorNumeric2());
        regra.setValorText(blankToNull(req.valorText()));
        regra.setValorBoolean(req.valorBoolean());
        regra.setResultado(req.resultado());
        regra.setSeveridade(req.severidade() == null ? 0 : req.severidade());
        regra.setPrioridade(req.prioridade() == null ? 0 : req.prioridade());
        regra.setAtivo(ativo);

        return toRegraResponse(regraRepo.save(regra));
    }

    @Transactional(readOnly = true)
    public List<RegraDTOs.RegraParametroResponse> listarRegras(UUID grupoId) {
        if (grupoId == null) throw new IllegalArgumentException("grupoId is required");
        return regraRepo.findAllByGrupoRegra_IdOrderByPrioridadeDescSeveridadeDesc(grupoId)
                .stream()
                .map(RegrasService::toRegraResponse)
                .toList();
    }

    @Transactional
    public RegraDTOs.SensorGrupoRegraResponse vincularGrupoAoSensor(String sensorExternalId, RegraDTOs.SensorGrupoRegraRequest req) {
        if (sensorExternalId == null || sensorExternalId.isBlank()) throw new IllegalArgumentException("sensorExternalId is required");
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.grupoRegraId() == null) throw new IllegalArgumentException("grupoRegraId is required");
        if (req.validoDe() != null && req.validoAte() != null && !req.validoAte().isAfter(req.validoDe())) {
            throw new IllegalArgumentException("validoAte must be after validoDe");
        }

        Sensor sensor = sensorRepo.findByExternalId(sensorExternalId.trim())
                .orElseThrow(() -> new NotFoundException("Sensor not found externalId=" + sensorExternalId));
        GrupoRegra grupo = grupoRepo.findById(req.grupoRegraId())
                .orElseThrow(() -> new NotFoundException("GrupoRegra not found id=" + req.grupoRegraId()));
        SensorGrupoRegraStatus status = req.status() == null ? SensorGrupoRegraStatus.RASCUNHO : req.status();
        validateGrupoSensorLink(sensor, grupo, status, req.validoDe(), req.validoAte());

        SensorGrupoRegra vinculo = sensorGrupoRepo.save(new SensorGrupoRegra(
                sensor,
                grupo,
                status,
                req.validoDe(),
                req.validoAte()
        ));
        return toSensorGrupoResponse(vinculo);
    }

    @Transactional(readOnly = true)
    public List<RegraDTOs.SensorGrupoRegraResponse> listarVinculosDoSensor(String sensorExternalId) {
        if (sensorExternalId == null || sensorExternalId.isBlank()) throw new IllegalArgumentException("sensorExternalId is required");
        return sensorGrupoRepo.findAllBySensor_ExternalIdOrderByCreatedAtDesc(sensorExternalId.trim())
                .stream()
                .map(RegrasService::toSensorGrupoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegraDTOs.ParametroDefResponse> listarParametros(String tipoNome) {
        if (tipoNome == null || tipoNome.isBlank()) throw new IllegalArgumentException("tipoNome is required");
        return parametroDefRepo.findAllByTipo_Nome(tipoNome.trim())
                .stream()
                .map(RegrasService::toParametroDefResponse)
                .toList();
    }

    private static RegraDTOs.GrupoRegraResponse toGrupoResponse(GrupoRegra grupo) {
        return new RegraDTOs.GrupoRegraResponse(
                grupo.getId(),
                grupo.getNome(),
                grupo.getDescricao(),
                grupo.isAtivo(),
                grupo.getCreatedAt()
        );
    }

    private static RegraDTOs.RegraParametroResponse toRegraResponse(RegraParametro regra) {
        return new RegraDTOs.RegraParametroResponse(
                regra.getId(),
                regra.getGrupoRegra().getId(),
                regra.getParametroDef().getId(),
                regra.getParametroDef().getNome(),
                regra.getNome(),
                regra.getDescricao(),
                regra.getOperador(),
                regra.getValorNumeric1(),
                regra.getValorNumeric2(),
                regra.getValorText(),
                regra.getValorBoolean(),
                regra.getResultado(),
                regra.getSeveridade(),
                regra.getPrioridade(),
                regra.isAtivo(),
                regra.getCreatedAt()
        );
    }

    private static RegraDTOs.SensorGrupoRegraResponse toSensorGrupoResponse(SensorGrupoRegra vinculo) {
        return new RegraDTOs.SensorGrupoRegraResponse(
                vinculo.getId(),
                vinculo.getSensor().getExternalId(),
                vinculo.getGrupoRegra().getId(),
                vinculo.getGrupoRegra().getNome(),
                vinculo.getStatus(),
                vinculo.getValidoDe(),
                vinculo.getValidoAte(),
                vinculo.getCreatedAt()
        );
    }

    private static RegraDTOs.ParametroDefResponse toParametroDefResponse(ParametroDef parametroDef) {
        return new RegraDTOs.ParametroDefResponse(
                parametroDef.getId(),
                parametroDef.getTipo().getNome(),
                parametroDef.getNome(),
                parametroDef.getDescricao(),
                parametroDef.getDataType().name(),
                parametroDef.getNumericUnit()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateGrupoSensorLink(
            Sensor sensor,
            GrupoRegra grupo,
            SensorGrupoRegraStatus status,
            Instant validoDe,
            Instant validoAte
    ) {
        if (status != SensorGrupoRegraStatus.ATIVO && status != SensorGrupoRegraStatus.AGENDADO) {
            return;
        }

        List<RegraParametro> proposedRules = regraRepo.findAllByGrupoRegra_IdAndAtivoTrueOrderByPrioridadeDescSeveridadeDesc(grupo.getId());
        if (proposedRules.isEmpty()) {
            throw new IllegalArgumentException("GrupoRegra must have at least one active rule before being linked as " + status);
        }

        String sensorTipoNome = sensor.getTipo().getNome();
        Set<UUID> proposedParametroIds = new HashSet<>();
        for (RegraParametro regra : proposedRules) {
            ParametroDef parametroDef = regra.getParametroDef();
            if (!sensorTipoNome.equals(parametroDef.getTipo().getNome())) {
                throw new IllegalArgumentException(
                        "RegraParametro id=" + regra.getId()
                                + " uses parametro tipo=" + parametroDef.getTipo().getNome()
                                + " but sensor tipo=" + sensorTipoNome
                );
            }
            if (!proposedParametroIds.add(parametroDef.getId())) {
                throw new IllegalArgumentException("GrupoRegra has more than one active rule for parametroDefId=" + parametroDef.getId());
            }
        }

        validateNoSensorRuleConflicts(sensor, proposedParametroIds, validoDe, validoAte, SensorGrupoRegraStatus.ATIVO);
        validateNoSensorRuleConflicts(sensor, proposedParametroIds, validoDe, validoAte, SensorGrupoRegraStatus.AGENDADO);
    }

    private void validateNoSensorRuleConflicts(
            Sensor sensor,
            Set<UUID> proposedParametroIds,
            Instant validoDe,
            Instant validoAte,
            SensorGrupoRegraStatus status
    ) {
        List<SensorGrupoRegra> links = sensorGrupoRepo.findAllBySensor_ExternalIdAndStatus(sensor.getExternalId(), status);
        for (SensorGrupoRegra link : links) {
            if (!validityWindowsOverlap(validoDe, validoAte, link.getValidoDe(), link.getValidoAte())) {
                continue;
            }

            List<RegraParametro> existingRules = regraRepo.findAllByGrupoRegra_IdAndAtivoTrueOrderByPrioridadeDescSeveridadeDesc(
                    link.getGrupoRegra().getId()
            );
            for (RegraParametro existingRule : existingRules) {
                UUID parametroDefId = existingRule.getParametroDef().getId();
                if (proposedParametroIds.contains(parametroDefId)) {
                    throw new IllegalArgumentException(
                            "Sensor " + sensor.getExternalId()
                                    + " already has a " + status
                                    + " rule for parametroDefId=" + parametroDefId
                                    + " in grupoRegraId=" + link.getGrupoRegra().getId()
                    );
                }
            }
        }
    }

    private static boolean validityWindowsOverlap(
            Instant startA,
            Instant endA,
            Instant startB,
            Instant endB
    ) {
        boolean aStartsBeforeBEnds = endB == null || startA == null || startA.isBefore(endB);
        boolean bStartsBeforeAEnds = endA == null || startB == null || startB.isBefore(endA);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    private static void validateRegra(ParametroDef parametroDef, RegraDTOs.RegraParametroRequest req) {
        switch (parametroDef.getDataType()) {
            case NUMERIC -> validateNumericRule(req);
            case BOOLEAN -> validateBooleanRule(req);
            case TEXT -> validateTextRule(req);
        }
    }

    private static void validateNumericRule(RegraDTOs.RegraParametroRequest req) {
        switch (req.operador()) {
            case GT, GTE, LT, LTE, EQ, NEQ -> {
                if (req.valorNumeric1() == null) {
                    throw new IllegalArgumentException("valorNumeric1 is required for numeric operator " + req.operador());
                }
            }
            case BETWEEN, OUTSIDE -> {
                if (req.valorNumeric1() == null || req.valorNumeric2() == null) {
                    throw new IllegalArgumentException("valorNumeric1 and valorNumeric2 are required for numeric operator " + req.operador());
                }
                if (req.valorNumeric2().compareTo(req.valorNumeric1()) < 0) {
                    throw new IllegalArgumentException("valorNumeric2 must be greater than or equal to valorNumeric1");
                }
            }
            case CONTAINS -> throw new IllegalArgumentException("CONTAINS is not supported for numeric parameters");
        }
    }

    private static void validateBooleanRule(RegraDTOs.RegraParametroRequest req) {
        if (req.operador() != RegraOperador.EQ && req.operador() != RegraOperador.NEQ) {
            throw new IllegalArgumentException("Only EQ and NEQ are supported for boolean parameters");
        }
        if (req.valorBoolean() == null) {
            throw new IllegalArgumentException("valorBoolean is required for boolean rules");
        }
    }

    private static void validateTextRule(RegraDTOs.RegraParametroRequest req) {
        if (req.operador() != RegraOperador.EQ
                && req.operador() != RegraOperador.NEQ
                && req.operador() != RegraOperador.CONTAINS) {
            throw new IllegalArgumentException("Only EQ, NEQ and CONTAINS are supported for text parameters");
        }
        if (req.valorText() == null || req.valorText().isBlank()) {
            throw new IllegalArgumentException("valorText is required for text rules");
        }
    }
}
