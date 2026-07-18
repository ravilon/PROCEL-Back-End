package com.procel.ingestion.service.sensors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.dto.seed.SensorSeedDTO;
import com.procel.ingestion.dto.seed.SensorsSeedFile;
import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.entity.sensors.Sensor;
import com.procel.ingestion.entity.sensors.TipoDeSensor;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;
import com.procel.ingestion.repository.sensors.TipoDeSensorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Service
public class SensorsSeedService {

    private final ObjectMapper om;
    private final String path;
    private final SensorRepository sensorRepo;
    private final TipoDeSensorRepository tipoRepo;
    private final CompartimentoRepository compartRepo;

    public SensorsSeedService(
            ObjectMapper om,
            @Value("${procel.sensors.seed-path:seed/sensors/sii-smart.sample.json}") String path,
            SensorRepository sensorRepo,
            TipoDeSensorRepository tipoRepo,
            CompartimentoRepository compartRepo
    ) {
        this.om = om;
        this.path = path;
        this.sensorRepo = sensorRepo;
        this.tipoRepo = tipoRepo;
        this.compartRepo = compartRepo;
    }

    @Transactional
    public String seedFromResource() {
        SensorsSeedFile file = readFileOptional(path);
        if (file.sensors() == null || file.sensors().isEmpty()) {
            return "No sensors in seed file (or file missing): " + path;
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (SensorSeedDTO s : file.sensors()) {
            if (s.externalId() == null || s.externalId().isBlank()
                    || s.tipoNome() == null || s.tipoNome().isBlank()
                    || s.compartimentoId() == null || s.compartimentoId().isBlank()) {
                skipped++;
                continue;
            }

            // garante tipo
            TipoDeSensor tipo = tipoRepo.findById(s.tipoNome())
                    .orElseGet(() -> tipoRepo.save(new TipoDeSensor(s.tipoNome())));

            var compOpt = compartRepo.findById(s.compartimentoId());
            if (compOpt.isEmpty()) {
                skipped++;
                continue;
            }
            Compartimento comp = compOpt.get();

            Sensor existing = sensorRepo.findById(s.externalId()).orElse(null);
            if (existing == null) {
                sensorRepo.save(new Sensor(s.externalId(), s.nome(), tipo, comp));
                created++;
            } else {
                existing.setNome(s.nome());
                existing.setTipo(tipo);
                existing.setCompartimento(comp);
                sensorRepo.save(existing);
                updated++;
            }
        }

        return "seedFromResource done. created=" + created + " updated=" + updated + " skipped=" + skipped + " path=" + path;
    }

    private SensorsSeedFile readFileOptional(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) return new SensorsSeedFile(List.of());

            try (InputStream in = resource.getInputStream()) {
                return om.readValue(in, SensorsSeedFile.class);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read sensors seed file from classpath: " + path, e);
        }
    }
}