package com.procel.api.service.sensors;

import com.procel.api.dto.sensors.RegraDTOs;
import com.procel.api.entity.sensors.AvaliacaoResultado;
import com.procel.api.entity.sensors.DataType;
import com.procel.api.entity.sensors.GrupoRegra;
import com.procel.api.entity.sensors.ParametroDef;
import com.procel.api.entity.sensors.RegraOperador;
import com.procel.api.entity.sensors.RegraParametro;
import com.procel.api.entity.sensors.Sensor;
import com.procel.api.entity.sensors.SensorGrupoRegra;
import com.procel.api.entity.sensors.SensorGrupoRegraStatus;
import com.procel.api.entity.sensors.TipoDeSensor;
import com.procel.api.repository.sensors.GrupoRegraRepository;
import com.procel.api.repository.sensors.ParametroDefRepository;
import com.procel.api.repository.sensors.RegraParametroRepository;
import com.procel.api.repository.sensors.SensorGrupoRegraRepository;
import com.procel.api.repository.sensors.SensorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class RegrasServiceTest {
    @Test
    void allowsMultipleActiveRulesForSameParameterInGroup() {
        GrupoRegraRepository grupoRepo = mock(GrupoRegraRepository.class);
        RegraParametroRepository regraRepo = mock(RegraParametroRepository.class);
        ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
        RegrasService service = new RegrasService(
                grupoRepo,
                regraRepo,
                parametroRepo,
                mock(SensorRepository.class),
                mock(SensorGrupoRegraRepository.class)
        );
        UUID grupoId = UUID.randomUUID();
        UUID parametroId = UUID.randomUUID();
        GrupoRegra grupo = new GrupoRegra("Grupo", null, true);
        ReflectionTestUtils.setField(grupo, "id", grupoId);
        ParametroDef parametro = new ParametroDef(
                new TipoDeSensor("SII_LIGHT"), "light", null, DataType.BOOLEAN, null);
        ReflectionTestUtils.setField(parametro, "id", parametroId);

        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(grupo));
        when(parametroRepo.findById(parametroId)).thenReturn(Optional.of(parametro));
        when(regraRepo.save(any(RegraParametro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criarRegra(grupoId, new RegraDTOs.RegraParametroRequest(
                parametroId,
                "Luz ligada ok",
                "light = true",
                RegraOperador.EQ,
                null,
                null,
                null,
                true,
                AvaliacaoResultado.IDEAL,
                0,
                1,
                true
        ));
        service.criarRegra(grupoId, new RegraDTOs.RegraParametroRequest(
                parametroId,
                "Luz desligada alerta",
                "light = false",
                RegraOperador.EQ,
                null,
                null,
                null,
                false,
                AvaliacaoResultado.ALERTA,
                2,
                2,
                true
        ));

        verify(regraRepo, times(2)).save(any(RegraParametro.class));
    }

    @Test
    void linksGroupWithMultipleRulesForSameParameterToSensor() {
        GrupoRegraRepository grupoRepo = mock(GrupoRegraRepository.class);
        RegraParametroRepository regraRepo = mock(RegraParametroRepository.class);
        SensorRepository sensorRepo = mock(SensorRepository.class);
        SensorGrupoRegraRepository sensorGrupoRepo = mock(SensorGrupoRegraRepository.class);
        RegrasService service = new RegrasService(
                grupoRepo,
                regraRepo,
                mock(ParametroDefRepository.class),
                sensorRepo,
                sensorGrupoRepo
        );
        UUID grupoId = UUID.randomUUID();
        UUID parametroId = UUID.randomUUID();
        TipoDeSensor tipo = new TipoDeSensor("SII_LIGHT");
        Sensor sensor = new Sensor("SII-LIGHT-001", "Sensor luz", tipo, null);
        GrupoRegra grupo = new GrupoRegra("Grupo luz", null, true);
        ReflectionTestUtils.setField(grupo, "id", grupoId);
        ParametroDef parametro = new ParametroDef(tipo, "light", null, DataType.BOOLEAN, null);
        ReflectionTestUtils.setField(parametro, "id", parametroId);
        RegraParametro onRule = regra(grupo, parametro, "Luz ligada ok", true, AvaliacaoResultado.IDEAL);
        RegraParametro offRule = regra(grupo, parametro, "Luz desligada alerta", false, AvaliacaoResultado.ALERTA);

        when(sensorRepo.findByExternalIdAndAtivoTrue("SII-LIGHT-001")).thenReturn(Optional.of(sensor));
        when(grupoRepo.findById(grupoId)).thenReturn(Optional.of(grupo));
        when(regraRepo.findAllByGrupoRegra_IdAndAtivoTrueOrderByPrioridadeDescSeveridadeDesc(grupoId))
                .thenReturn(List.of(onRule, offRule));
        when(sensorGrupoRepo.save(any(SensorGrupoRegra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.vincularGrupoAoSensor(
                "SII-LIGHT-001",
                new RegraDTOs.SensorGrupoRegraRequest(grupoId, SensorGrupoRegraStatus.ATIVO, null, null)
        );

        assertThat(response.sensorExternalId()).isEqualTo("SII-LIGHT-001");
        assertThat(response.status()).isEqualTo(SensorGrupoRegraStatus.ATIVO);
    }

    @Test
    void updatesRuleWithoutReplacingItsId() {
        RegraParametroRepository regraRepo = mock(RegraParametroRepository.class);
        ParametroDefRepository parametroRepo = mock(ParametroDefRepository.class);
        RegrasService service = service(regraRepo, parametroRepo);
        UUID grupoId = UUID.randomUUID();
        UUID regraId = UUID.randomUUID();
        UUID parametroId = UUID.randomUUID();
        GrupoRegra grupo = new GrupoRegra("Grupo", null, true);
        ReflectionTestUtils.setField(grupo, "id", grupoId);
        ParametroDef parametro = new ParametroDef(
                new TipoDeSensor("SII_SMART"), "temperature", null, DataType.NUMERIC, "C");
        ReflectionTestUtils.setField(parametro, "id", parametroId);
        RegraParametro regra = new RegraParametro();
        ReflectionTestUtils.setField(regra, "id", regraId);
        regra.setGrupoRegra(grupo);
        regra.setParametroDef(parametro);

        when(regraRepo.findById(regraId)).thenReturn(Optional.of(regra));
        when(parametroRepo.findById(parametroId)).thenReturn(Optional.of(parametro));
        when(regraRepo.findAllByGrupoRegra_IdAndParametroDef_IdAndAtivoTrue(grupoId, parametroId))
                .thenReturn(List.of(regra));
        when(regraRepo.save(any(RegraParametro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.atualizarRegra(
                grupoId,
                regraId,
                new RegraDTOs.RegraParametroRequest(
                        parametroId,
                        "Temperatura critica",
                        "Atualizada",
                        RegraOperador.GT,
                        BigDecimal.valueOf(30),
                        null,
                        null,
                        null,
                        AvaliacaoResultado.CRITICO,
                        5,
                        10,
                        true
                )
        );

        assertThat(response.id()).isEqualTo(regraId);
        assertThat(response.nome()).isEqualTo("Temperatura critica");
        assertThat(response.valorNumeric1()).isEqualByComparingTo("30");
        assertThat(response.ativo()).isTrue();
    }

    @Test
    void removingRuleDeletesItAndItsEvaluations() {
        RegraParametroRepository regraRepo = mock(RegraParametroRepository.class);
        RegrasService service = service(regraRepo, mock(ParametroDefRepository.class));
        UUID grupoId = UUID.randomUUID();
        UUID regraId = UUID.randomUUID();
        GrupoRegra grupo = new GrupoRegra("Grupo", null, true);
        ReflectionTestUtils.setField(grupo, "id", grupoId);
        RegraParametro regra = new RegraParametro();
        regra.setGrupoRegra(grupo);
        regra.setAtivo(true);
        when(regraRepo.findById(regraId)).thenReturn(Optional.of(regra));

        service.removerRegra(grupoId, regraId);
        verify(regraRepo).delete(regra);
    }

    @Test
    void removesGroupLinkThatBelongsToSensor() {
        SensorGrupoRegraRepository sensorGrupoRepo = mock(SensorGrupoRegraRepository.class);
        RegrasService service = service(
                mock(RegraParametroRepository.class),
                mock(ParametroDefRepository.class),
                sensorGrupoRepo
        );
        UUID vinculoId = UUID.randomUUID();
        TipoDeSensor tipo = new TipoDeSensor("SII_SMART");
        Sensor sensor = new Sensor("SII-001", "Sensor 1", tipo, null);
        GrupoRegra grupo = new GrupoRegra("Grupo", null, true);
        SensorGrupoRegra vinculo = new SensorGrupoRegra(
                sensor,
                grupo,
                SensorGrupoRegraStatus.ATIVO,
                null,
                null
        );
        when(sensorGrupoRepo.findById(vinculoId)).thenReturn(Optional.of(vinculo));

        service.removerVinculoDoSensor("SII-001", vinculoId);

        verify(sensorGrupoRepo).delete(vinculo);
    }

    private static RegraParametro regra(
            GrupoRegra grupo,
            ParametroDef parametro,
            String nome,
            boolean valor,
            AvaliacaoResultado resultado
    ) {
        RegraParametro regra = new RegraParametro();
        regra.setGrupoRegra(grupo);
        regra.setParametroDef(parametro);
        regra.setNome(nome);
        regra.setOperador(RegraOperador.EQ);
        regra.setValorBoolean(valor);
        regra.setResultado(resultado);
        regra.setAtivo(true);
        return regra;
    }

    private static RegrasService service(
            RegraParametroRepository regraRepo,
            ParametroDefRepository parametroRepo
    ) {
        return service(regraRepo, parametroRepo, mock(SensorGrupoRegraRepository.class));
    }

    private static RegrasService service(
            RegraParametroRepository regraRepo,
            ParametroDefRepository parametroRepo,
            SensorGrupoRegraRepository sensorGrupoRepo
    ) {
        return new RegrasService(
                mock(GrupoRegraRepository.class),
                regraRepo,
                parametroRepo,
                mock(SensorRepository.class),
                sensorGrupoRepo
        );
    }
}
