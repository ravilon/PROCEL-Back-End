package com.procel.ingestion.service.sensors;

import com.procel.ingestion.entity.sensors.Medicao;
import com.procel.ingestion.entity.sensors.ParametroDef;
import com.procel.ingestion.entity.sensors.ParametroValor;
import com.procel.ingestion.entity.sensors.Sensor;

import com.procel.ingestion.repository.sensors.MedicaoRepository;
import com.procel.ingestion.repository.sensors.ParametroDefRepository;
import com.procel.ingestion.repository.sensors.ParametroValorRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;

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

    public SensorIngestionService(
            SensorRepository sensorRepo,
            ParametroDefRepository parametroDefRepo,
            MedicaoRepository medicaoRepo,
            ParametroValorRepository parametroValorRepo
    ) {
        this.sensorRepo = sensorRepo;
        this.parametroDefRepo = parametroDefRepo;
        this.medicaoRepo = medicaoRepo;
        this.parametroValorRepo = parametroValorRepo;
    }

    public void ingest(RawSensorEvent event) {
        // Sensor PK = external_id (String)
        Sensor sensor = sensorRepo.findByExternalId(event.sensorExternalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Sensor not found for externalId=" + event.sensorExternalId()
                ));

        Medicao medicao = new Medicao(
                sensor,
                nvl(event.timestamp(), Instant.now()),
                event.receivedAt(),
                event.source()
        );
        medicao = medicaoRepo.save(medicao);

        // TipoDeSensor PK = nome (String)
        String tipoNome = sensor.getTipo().getNome();

        for (Map.Entry<String, Object> e : event.payload().entrySet()) {
            String key = e.getKey();
            Object rawValue = e.getValue();

            ParametroDef def = parametroDefRepo.findByTipo_NomeAndNome(tipoNome, key)
                    .orElseThrow(() -> new IllegalStateException(
                            "ParametroDef not found: tipo=" + tipoNome + " key=" + key
                    ));

            // integridade: def deve ser do mesmo tipo do sensor
            if (!Objects.equals(def.getTipo().getNome(), tipoNome)) {
                throw new IllegalStateException(
                        "ParametroDef tipo mismatch for key=" + key +
                        " (expected tipo=" + tipoNome + ", got tipo=" + def.getTipo().getNome() + ")"
                );
            }

            ParametroValor valor = new ParametroValor(medicao, def);

            // MVP: sem semântica avançada. Só tipagem e persistência.
            switch (def.getDataType()) {
                case BOOLEAN -> valor.setBooleanValue(coerceBoolean(rawValue));
                case TEXT -> valor.setTextValue(rawValue != null ? rawValue.toString() : null);
                case NUMERIC -> valor.setNumericValue(coerceNumeric(rawValue));
            }

            parametroValorRepo.save(valor);
        }
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