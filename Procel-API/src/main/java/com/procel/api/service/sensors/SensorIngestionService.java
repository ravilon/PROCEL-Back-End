package com.procel.api.service.sensors;

import com.procel.api.entity.sensors.*;

import com.procel.api.repository.sensors.MedicaoRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.ParametroValorRepository;
import com.procel.api.repository.sensors.SensorRepository;
import com.procel.api.exception.ApiStatusException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class SensorIngestionService {

    private final SensorRepository sensorRepo;
    private final ParametroDefRepository parametroDefRepo;
    private final MedicaoRepository medicaoRepo;
    private final ParametroValorRepository parametroValorRepo;
    private final ParametroQualificacaoService qualificacaoService;

    public SensorIngestionService(
            SensorRepository sensorRepo,
            ParametroDefRepository parametroDefRepo,
            MedicaoRepository medicaoRepo,
            ParametroValorRepository parametroValorRepo,
            ParametroQualificacaoService qualificacaoService
    ) {
        this.sensorRepo = sensorRepo;
        this.parametroDefRepo = parametroDefRepo;
        this.medicaoRepo = medicaoRepo;
        this.parametroValorRepo = parametroValorRepo;
        this.qualificacaoService = qualificacaoService;
    }

    public void ingest(RawSensorEvent event) {
        ingestAndReturn(event);
    }

    public Medicao ingestAndReturn(RawSensorEvent event) {
        // Sensor PK = external_id (String)
        Sensor sensor = sensorRepo.findByExternalIdAndAtivoTrue(event.sensorExternalId())
                .orElseThrow(() -> new ApiStatusException(
                        HttpStatus.NOT_FOUND,
                        "SENSOR_NOT_FOUND",
                        "Active sensor not found: " + event.sensorExternalId()
                ));

        Instant measuredAt = nvl(event.timestamp(), Instant.now());
        Medicao medicao = new Medicao(
                sensor,
                measuredAt,
                event.receivedAt(),
                event.source()
        );
        medicao = medicaoRepo.save(medicao);

        // TipoDeSensor PK = nome (String)
        String tipoNome = sensor.getTipo().getNome();

        for (Map.Entry<String, Object> e : event.payload().entrySet()) {
            String key = e.getKey();
            Object rawValue = e.getValue();

            ParametroDef def = parametroDefRepo.findByTipo_NomeAndNomeAndAtivoTrue(tipoNome, key)
                    .orElseThrow(() -> new ApiStatusException(
                            HttpStatus.UNPROCESSABLE_CONTENT,
                            "PARAMETER_NOT_ACCEPTED",
                            "Active ParametroDef not found: tipo=" + tipoNome + " key=" + key
                    ));

            // integridade: def deve ser do mesmo tipo do sensor
            if (!Objects.equals(def.getTipo().getNome(), tipoNome)) {
                throw new ApiStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "PARAMETER_NOT_ACCEPTED",
                        "ParametroDef tipo mismatch for key=" + key +
                        " (expected tipo=" + tipoNome + ", got tipo=" + def.getTipo().getNome() + ")"
                );
            }

            ParametroValor valor = new ParametroValor(medicao, def);

            // MVP: sem semântica avançada. Só tipagem e persistência.
            try {
                switch (def.getDataType()) {
                    case BOOLEAN -> valor.setBooleanValue(coerceBoolean(rawValue));
                    case TEXT -> valor.setTextValue(rawValue != null ? rawValue.toString() : null);
                    case NUMERIC -> valor.setNumericValue(coerceNumeric(rawValue));
                }
            } catch (IllegalArgumentException ex) {
                throw new ApiStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "VALUE_TYPE_INVALID",
                        "Invalid value for parameter " + key + ": " + ex.getMessage()
                );
            }

            valor = parametroValorRepo.save(valor);
            qualificacaoService.avaliar(valor, sensor, measuredAt);
        }
        return medicao;
    }

    private Instant nvl(Instant v, Instant fallback) {
        return v != null ? v : fallback;
    }

    private Boolean coerceBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;

        String s = v.toString().trim().toLowerCase();
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;

        throw new IllegalArgumentException("Cannot coerce boolean from: " + v);
    }

    private BigDecimal coerceNumeric(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());

        String s = v.toString().trim().replace(",", ".");
        return new BigDecimal(s);
    }
}
