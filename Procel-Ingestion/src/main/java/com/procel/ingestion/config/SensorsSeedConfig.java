package com.procel.ingestion.config;

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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Configuration
public class SensorsSeedConfig {

    @Bean
    CommandLineRunner seedSensorsFromJson(
            ObjectMapper om,
            @Value("${procel.sensors.seed-path:seed/sensors/sii-smart.sample.json}") String path,
            SensorRepository sensorRepo,
            TipoDeSensorRepository tipoRepo,
            CompartimentoRepository compartRepo
    ) {
        return args -> seed(om, path, sensorRepo, tipoRepo, compartRepo);
    }

    @Transactional
    void seed(
            ObjectMapper om,
            String path,
            SensorRepository sensorRepo,
            TipoDeSensorRepository tipoRepo,
            CompartimentoRepository compartRepo
    ) {
        SensorsSeedFile file = readFileOptional(om, path);
        if (file.sensors() == null || file.sensors().isEmpty()) return;

        for (SensorSeedDTO s : file.sensors()) {
            validate(s);

            // ✅ Robustez: garante tipo independentemente da ordem de runners
            TipoDeSensor tipo = tipoRepo.findById(s.tipoNome())
                    .orElseGet(() -> tipoRepo.save(new TipoDeSensor(s.tipoNome())));

            var compOpt = compartRepo.findById(s.compartimentoId());
            if (compOpt.isEmpty()) {
                System.out.println("[SensorsSeedConfig] Compartimento not found id=" + s.compartimentoId()
                        + " (rooms ainda não sincronizados). Skipping sensor externalId=" + s.externalId());
                continue;
            }
            Compartimento comp = compOpt.get();

            Sensor sensor = sensorRepo.findById(s.externalId()).orElse(null);
            if (sensor == null) {
                sensorRepo.save(new Sensor(s.externalId(), s.nome(), tipo, comp));
            } else {
                sensor.setNome(s.nome());
                sensor.setTipo(tipo);
                sensor.setCompartimento(comp);
                sensorRepo.save(sensor);
            }
        }
    }

    private void validate(SensorSeedDTO s) {
        if (s.externalId() == null || s.externalId().isBlank())
            throw new IllegalStateException("seed sensor externalId is required");
        if (s.nome() == null || s.nome().isBlank())
            throw new IllegalStateException("seed sensor nome is required for externalId=" + s.externalId());
        if (s.tipoNome() == null || s.tipoNome().isBlank())
            throw new IllegalStateException("seed sensor tipoNome is required for externalId=" + s.externalId());
        if (s.compartimentoId() == null || s.compartimentoId().isBlank())
            throw new IllegalStateException("seed sensor compartimentoId is required for externalId=" + s.externalId());
    }

    private SensorsSeedFile readFileOptional(ObjectMapper om, String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                System.out.println("[SensorsSeedConfig] Seed file not found: " + path + " (skipping)");
                return new SensorsSeedFile(List.of());
            }
            try (InputStream in = resource.getInputStream()) {
                return om.readValue(in, SensorsSeedFile.class);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read sensors seed file from classpath: " + path, e);
        }
    }
}