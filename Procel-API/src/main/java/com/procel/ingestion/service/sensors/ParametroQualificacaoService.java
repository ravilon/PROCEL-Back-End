package com.procel.ingestion.service.sensors;

import com.procel.ingestion.entity.sensors.*;
import com.procel.ingestion.repository.sensors.AvaliacaoParametroValorRepository;
import com.procel.ingestion.repository.sensors.RegraParametroRepository;
import com.procel.ingestion.repository.sensors.SensorGrupoRegraRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ParametroQualificacaoService {

    private final SensorGrupoRegraRepository sensorGrupoRegraRepo;
    private final RegraParametroRepository regraParametroRepo;
    private final AvaliacaoParametroValorRepository avaliacaoRepo;

    public ParametroQualificacaoService(
            SensorGrupoRegraRepository sensorGrupoRegraRepo,
            RegraParametroRepository regraParametroRepo,
            AvaliacaoParametroValorRepository avaliacaoRepo
    ) {
        this.sensorGrupoRegraRepo = sensorGrupoRegraRepo;
        this.regraParametroRepo = regraParametroRepo;
        this.avaliacaoRepo = avaliacaoRepo;
    }

    public void avaliar(ParametroValor valor, Sensor sensor, Instant timestamp) {
        List<SensorGrupoRegra> gruposEfetivos = sensorGrupoRegraRepo.findEffectiveGroups(
                sensor.getExternalId(),
                SensorGrupoRegraStatus.ATIVO,
                timestamp
        );
        avaliar(valor, distinctByGrupoRegra(gruposEfetivos));
    }

    private List<SensorGrupoRegra> distinctByGrupoRegra(List<SensorGrupoRegra> grupos) {
        Map<UUID, SensorGrupoRegra> byGrupoId = new LinkedHashMap<>();
        for (SensorGrupoRegra grupo : grupos) {
            byGrupoId.putIfAbsent(grupo.getGrupoRegra().getId(), grupo);
        }
        return List.copyOf(byGrupoId.values());
    }

    private void avaliar(ParametroValor valor, List<SensorGrupoRegra> gruposEfetivos) {
        if (gruposEfetivos.isEmpty()) return;

        for (SensorGrupoRegra grupo : gruposEfetivos) {
            List<RegraParametro> regras = regraParametroRepo.findAllByGrupoRegra_IdAndParametroDef_IdAndAtivoTrue(
                    grupo.getGrupoRegra().getId(),
                    valor.getParametroDef().getId()
            );

            for (RegraParametro regra : regras) {
                if (matches(regra, valor)) {
                    avaliacaoRepo.save(new AvaliacaoParametroValor(
                            valor,
                            regra,
                            regra.getResultado(),
                            regra.getSeveridade(),
                            "Regra aplicada: " + regra.getNome()
                    ));
                }
            }
        }
    }

    private boolean matches(RegraParametro regra, ParametroValor valor) {
        return switch (valor.getParametroDef().getDataType()) {
            case NUMERIC -> matchesNumeric(regra, valor.getNumericValue());
            case BOOLEAN -> matchesBoolean(regra, valor.getBooleanValue());
            case TEXT -> matchesText(regra, valor.getTextValue());
        };
    }

    private boolean matchesNumeric(RegraParametro regra, BigDecimal value) {
        if (value == null) return false;
        BigDecimal v1 = regra.getValorNumeric1();
        BigDecimal v2 = regra.getValorNumeric2();

        return switch (regra.getOperador()) {
            case GT -> v1 != null && value.compareTo(v1) > 0;
            case GTE -> v1 != null && value.compareTo(v1) >= 0;
            case LT -> v1 != null && value.compareTo(v1) < 0;
            case LTE -> v1 != null && value.compareTo(v1) <= 0;
            case EQ -> v1 != null && value.compareTo(v1) == 0;
            case NEQ -> v1 != null && value.compareTo(v1) != 0;
            case BETWEEN -> v1 != null && v2 != null && value.compareTo(v1) >= 0 && value.compareTo(v2) <= 0;
            case OUTSIDE -> v1 != null && v2 != null && (value.compareTo(v1) < 0 || value.compareTo(v2) > 0);
            case CONTAINS -> false;
        };
    }

    private boolean matchesBoolean(RegraParametro regra, Boolean value) {
        if (value == null || regra.getValorBoolean() == null) return false;

        return switch (regra.getOperador()) {
            case EQ -> Objects.equals(value, regra.getValorBoolean());
            case NEQ -> !Objects.equals(value, regra.getValorBoolean());
            default -> false;
        };
    }

    private boolean matchesText(RegraParametro regra, String value) {
        if (value == null || regra.getValorText() == null) return false;

        return switch (regra.getOperador()) {
            case EQ -> value.equals(regra.getValorText());
            case NEQ -> !value.equals(regra.getValorText());
            case CONTAINS -> value.contains(regra.getValorText());
            default -> false;
        };
    }
}
