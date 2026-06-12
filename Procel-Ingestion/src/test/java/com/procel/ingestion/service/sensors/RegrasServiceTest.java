package com.procel.ingestion.service.sensors;

import com.procel.ingestion.dto.sensors.RegraDTOs;
import com.procel.ingestion.entity.sensors.AvaliacaoResultado;
import com.procel.ingestion.entity.sensors.DataType;
import com.procel.ingestion.entity.sensors.GrupoRegra;
import com.procel.ingestion.entity.sensors.ParametroDef;
import com.procel.ingestion.entity.sensors.RegraOperador;
import com.procel.ingestion.entity.sensors.RegraParametro;
import com.procel.ingestion.entity.sensors.TipoDeSensor;
import com.procel.ingestion.repository.sensors.GrupoRegraRepository;
import com.procel.ingestion.repository.sensors.ParametroDefRepository;
import com.procel.ingestion.repository.sensors.RegraParametroRepository;
import com.procel.ingestion.repository.sensors.SensorGrupoRegraRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;
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
import static org.mockito.Mockito.when;

class RegrasServiceTest {

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
    void removingRuleOnlyDeactivatesIt() {
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

        assertThat(regra.isAtivo()).isFalse();
        verify(regraRepo).save(regra);
    }

    private static RegrasService service(
            RegraParametroRepository regraRepo,
            ParametroDefRepository parametroRepo
    ) {
        return new RegrasService(
                mock(GrupoRegraRepository.class),
                regraRepo,
                parametroRepo,
                mock(SensorRepository.class),
                mock(SensorGrupoRegraRepository.class)
        );
    }
}
