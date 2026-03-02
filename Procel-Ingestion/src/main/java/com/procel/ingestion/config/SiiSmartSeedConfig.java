package com.procel.ingestion.config;

import com.procel.ingestion.entity.sensors.DataType;
import com.procel.ingestion.entity.sensors.ParametroDef;
import com.procel.ingestion.entity.sensors.TipoDeSensor;
import com.procel.ingestion.repository.sensors.ParametroDefRepository;
import com.procel.ingestion.repository.sensors.TipoDeSensorRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SiiSmartSeedConfig {

    @Bean
    CommandLineRunner seedSiiSmart(TipoDeSensorRepository tipoRepo, ParametroDefRepository defRepo) {
        return args -> {
            TipoDeSensor sii = tipoRepo.findByNome("SII_SMART")
                    .orElseGet(() -> tipoRepo.save(new TipoDeSensor("SII_SMART")));

            seed(defRepo, sii, "temperature_c", "Temperatura", DataType.NUMERIC, "C");
            seed(defRepo, sii, "humidity_pct", "Umidade", DataType.NUMERIC, "%");
            seed(defRepo, sii, "presence", "Presença", DataType.BOOLEAN, null);
            seed(defRepo, sii, "noise_db", "Ruído ambiente", DataType.NUMERIC, "dB");
            seed(defRepo, sii, "energy_total_room", "Consumo de energia total da sala", DataType.NUMERIC, "kWh");
            seed(defRepo, sii, "ac_setpoint_c", "Set point do ar-condicionado", DataType.NUMERIC, "C");
            seed(defRepo, sii, "ac_status", "Status do ar-condicionado", DataType.BOOLEAN, null);
            seed(defRepo, sii, "light_status", "Status da iluminação", DataType.BOOLEAN, null);
            seed(defRepo, sii, "energy_ac", "Consumo do ar-condicionado", DataType.NUMERIC, "kWh");
            seed(defRepo, sii, "energy_lighting", "Consumo da iluminação", DataType.NUMERIC, "kWh");
            seed(defRepo, sii, "energy_room_circuit", "Consumo do circuito da sala", DataType.NUMERIC, "kWh");
        };
    }

    private void seed(ParametroDefRepository defRepo, TipoDeSensor tipo, String nome, String desc, DataType dt, String unit) {
        defRepo.findByTipo_NomeAndNome(tipo.getNome(), nome).orElseGet(() -> defRepo.save(new ParametroDef(tipo, nome, desc, dt, unit)));
    }
}