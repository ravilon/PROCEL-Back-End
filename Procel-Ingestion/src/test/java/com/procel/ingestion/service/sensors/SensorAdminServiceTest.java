package com.procel.ingestion.service.sensors;

import com.procel.ingestion.dto.sensors.SensorAdminDTOs;
import com.procel.ingestion.entity.sensors.DataType;
import com.procel.ingestion.entity.sensors.ParametroDef;
import com.procel.ingestion.entity.sensors.Sensor;
import com.procel.ingestion.entity.sensors.TipoDeSensor;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import com.procel.ingestion.repository.sensors.ParametroDefRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;
import com.procel.ingestion.repository.sensors.TipoDeSensorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensorAdminServiceTest {

    @Test
    void updatesParameterWithoutReplacingItsId() {
        TipoDeSensorRepository tipoRepo = mock(TipoDeSensorRepository.class);
        ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
        SensorAdminService service = service(tipoRepo, parametroRepo, mock(EntityManager.class));
        TipoDeSensor tipo = new TipoDeSensor("SII_SMART");
        ParametroDef parametro = new ParametroDef(tipo, "temperature", null, DataType.NUMERIC, "C");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(parametro, "id", id);

        when(parametroRepo.findById(id)).thenReturn(Optional.of(parametro));
        when(parametroRepo.findByTipo_NomeAndNome("SII_SMART", "temperature_c"))
                .thenReturn(Optional.empty());
        when(parametroRepo.save(any(ParametroDef.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.atualizarParametro(
                id,
                new SensorAdminDTOs.ParametroRequest(
                        "temperature_c", "Temperatura ambiente", DataType.TEXT, "ignored")
        );

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("temperature_c");
        assertThat(response.descricao()).isEqualTo("Temperatura ambiente");
        assertThat(response.dataType()).isEqualTo(DataType.TEXT);
        assertThat(response.numericUnit()).isNull();
    }

    @Test
    void renamesTypeThroughCascadeUpdate() {
        TipoDeSensorRepository tipoRepo = mock(TipoDeSensorRepository.class);
        ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        SensorAdminService service = service(tipoRepo, parametroRepo, entityManager);
        TipoDeSensor renamed = new TipoDeSensor("SII_SMART_V2");

        when(tipoRepo.existsById("SII_SMART")).thenReturn(true);
        when(tipoRepo.existsById("SII_SMART_V2")).thenReturn(false);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(query);
        when(query.setParameter(any(String.class), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        when(tipoRepo.findById("SII_SMART_V2")).thenReturn(Optional.of(renamed));
        when(parametroRepo.findAllByTipo_Nome("SII_SMART_V2")).thenReturn(List.of());

        var response = service.atualizarTipo(
                "SII_SMART",
                new SensorAdminDTOs.TipoSensorUpdateRequest("SII_SMART_V2")
        );

        assertThat(response.nome()).isEqualTo("SII_SMART_V2");
        verify(entityManager).clear();
        verify(query).executeUpdate();
    }

    @Test
    void hidesAndRestoresParameterWithoutDeletingIt() {
        TipoDeSensorRepository tipoRepo = mock(TipoDeSensorRepository.class);
        ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
        SensorAdminService service = service(tipoRepo, parametroRepo, mock(EntityManager.class));
        ParametroDef parametro = new ParametroDef(
                new TipoDeSensor("SII_SMART"), "temperature", null, DataType.NUMERIC, "C");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(parametro, "id", id);
        when(parametroRepo.findById(id)).thenReturn(Optional.of(parametro));
        when(parametroRepo.save(parametro)).thenReturn(parametro);

        service.ocultarParametro(id);
        assertThat(parametro.isAtivo()).isFalse();

        var restored = service.reativarParametro(id);
        assertThat(restored.ativo()).isTrue();
        verify(parametroRepo, org.mockito.Mockito.times(2)).save(parametro);
    }

    @Test
    void hidesAndRestoresSensorWithoutDeletingIt() {
        TipoDeSensorRepository tipoRepo = mock(TipoDeSensorRepository.class);
        ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
        SensorRepository sensorRepo = mock(SensorRepository.class);
        CompartimentoRepository compartimentoRepo = mock(CompartimentoRepository.class);
        SensorAdminService service = new SensorAdminService(
                tipoRepo, parametroRepo, sensorRepo, compartimentoRepo, mock(EntityManager.class));
        var compartimento = new com.procel.ingestion.entity.rooms.Compartimento(
                "room-1", null, null, "Sala 1", "Sala");
        Sensor sensor = new Sensor(
                "sensor-1", "Sensor", new TipoDeSensor("SII_SMART"), compartimento);
        when(sensorRepo.findById("sensor-1")).thenReturn(Optional.of(sensor));
        when(sensorRepo.save(sensor)).thenReturn(sensor);

        service.ocultarSensor("sensor-1");
        assertThat(sensor.isAtivo()).isFalse();
        var restored = service.reativarSensor("sensor-1");

        assertThat(restored.ativo()).isTrue();
    }

    private static SensorAdminService service(
            TipoDeSensorRepository tipoRepo,
            ParametroDefRepository parametroRepo,
            EntityManager entityManager
    ) {
        return new SensorAdminService(
                tipoRepo,
                parametroRepo,
                mock(SensorRepository.class),
                mock(CompartimentoRepository.class),
                entityManager
        );
    }
}
