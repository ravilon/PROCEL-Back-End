package com.procel.ingestion.service.sensors;

import com.procel.ingestion.entity.sensors.*;
import com.procel.ingestion.repository.sensors.MedicaoRepository;
import com.procel.ingestion.repository.sensors.ParametroDefRepository;
import com.procel.ingestion.repository.sensors.ParametroValorRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class SensorsMockIngestService {

    private final SensorRepository sensorRepo;
    private final ParametroDefRepository defRepo;
    private final MedicaoRepository medicaoRepo;
    private final ParametroValorRepository valorRepo;

    public SensorsMockIngestService(
            SensorRepository sensorRepo,
            ParametroDefRepository defRepo,
            MedicaoRepository medicaoRepo,
            ParametroValorRepository valorRepo
    ) {
        this.sensorRepo = sensorRepo;
        this.defRepo = defRepo;
        this.medicaoRepo = medicaoRepo;
        this.valorRepo = valorRepo;
    }

    @Transactional
    public MockIngestResponse ingest(MockIngestRequest reqNullable) {
        MockIngestRequest req = normalize(reqNullable);

        Sensor sensor = resolveSensor(req.sensorExternalId());
        String tipoNome = sensor.getTipo().getNome();

        List<ParametroDef> defs = defRepo.findAllByTipo_Nome(tipoNome);
        if (defs.isEmpty()) {
            throw new IllegalStateException("No ParametroDef for tipo=" + tipoNome + ". Rode o seed antes.");
        }

        Instant now = Instant.now();
        Instant start = now.minusSeconds((long) req.minutesBack() * 60L);

        int medCount = 0;
        int valCount = 0;

        for (Instant t = start; !t.isAfter(now); t = t.plusSeconds(req.everySeconds())) {
            Medicao med = medicaoRepo.save(new Medicao(sensor, t, now, req.source()));
            medCount++;

            // ✅ Snapshot completo: gera 1 valor por ParametroDef
            for (ParametroDef def : defs) {
                ParametroValor pv = new ParametroValor(med, def);

                switch (def.getDataType()) {
                    case BOOLEAN -> pv.setBooleanValue(mockBoolean(def.getNome(), t, sensor.getExternalId()));
                    case TEXT -> pv.setTextValue(mockText(def.getNome(), t, sensor.getExternalId()));
                    case NUMERIC -> pv.setNumericValue(mockNumeric(def.getNome(), t, sensor.getExternalId()));
                }

                valorRepo.save(pv);
                valCount++;
            }
        }

        return new MockIngestResponse(sensor.getExternalId(), tipoNome, defs.size(), medCount, valCount);
    }

    private MockIngestRequest normalize(MockIngestRequest r) {
        if (r == null) return new MockIngestRequest(null, 60, 10, "mock");

        Integer minutesBack = (r.minutesBack() == null || r.minutesBack() <= 0) ? 60 : r.minutesBack();
        Integer everySeconds = (r.everySeconds() == null || r.everySeconds() <= 0) ? 10 : r.everySeconds();
        String source = (r.source() == null || r.source().isBlank()) ? "mock" : r.source().trim();

        return new MockIngestRequest(r.sensorExternalId(), minutesBack, everySeconds, source);
    }

    private Sensor resolveSensor(String sensorExternalId) {
        if (sensorExternalId != null && !sensorExternalId.isBlank()) {
            return sensorRepo.findById(sensorExternalId)
                    .orElseThrow(() -> new IllegalStateException("Sensor not found: " + sensorExternalId));
        }

        return sensorRepo.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Sensor found. Rode o seed do sensor antes."));
    }

    // -----------------------------
    // Mock generators (plausíveis e determinísticos)
    // -----------------------------

    private Boolean mockBoolean(String key, Instant t, String sensorId) {
        long bucket = t.getEpochSecond() / 30;
        return (Objects.hash(key, sensorId, bucket) & 1) == 0;
    }

    private String mockText(String key, Instant t, String sensorId) {
        return "mock:" + key + ":" + sensorId + ":" + t.getEpochSecond();
    }

    private BigDecimal mockNumeric(String key, Instant t, String sensorId) {
        long minute = t.getEpochSecond() / 60;
        int h = Math.floorMod(Objects.hash(key, sensorId, minute), 10000);

        if (key.contains("temperature")) return BigDecimal.valueOf(20.0 + (h % 800) / 100.0);
        if (key.contains("humidity")) return BigDecimal.valueOf(35.0 + (h % 400) / 10.0);
        if (key.contains("noise")) return BigDecimal.valueOf(35.0 + (h % 450) / 10.0);

        if (key.equals("ac_setpoint_c")) return BigDecimal.valueOf(18.0 + (h % 800) / 100.0);

        if (key.startsWith("energy_")) {
            // kWh por evento
            return BigDecimal.valueOf(0.05 + (h % 1950) / 1000.0);
        }

        return BigDecimal.valueOf((h % 10000) / 100.0);
    }
}