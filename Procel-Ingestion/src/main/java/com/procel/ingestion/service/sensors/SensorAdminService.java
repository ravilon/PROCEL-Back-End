package com.procel.ingestion.service.sensors;

import com.procel.ingestion.dto.sensors.SensorAdminDTOs;
import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.entity.sensors.ParametroDef;
import com.procel.ingestion.entity.sensors.Sensor;
import com.procel.ingestion.entity.sensors.TipoDeSensor;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import com.procel.ingestion.repository.sensors.ParametroDefRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;
import com.procel.ingestion.repository.sensors.TipoDeSensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class SensorAdminService {

    private final TipoDeSensorRepository tipoRepo;
    private final ParametroDefRepository parametroRepo;
    private final SensorRepository sensorRepo;
    private final CompartimentoRepository compartimentoRepo;
    private final EntityManager entityManager;

    public SensorAdminService(
            TipoDeSensorRepository tipoRepo,
            ParametroDefRepository parametroRepo,
            SensorRepository sensorRepo,
            CompartimentoRepository compartimentoRepo,
            EntityManager entityManager
    ) {
        this.tipoRepo = tipoRepo;
        this.parametroRepo = parametroRepo;
        this.sensorRepo = sensorRepo;
        this.compartimentoRepo = compartimentoRepo;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<SensorAdminDTOs.TipoSensorResponse> listarTipos() {
        return tipoRepo.findAll().stream()
                .sorted(Comparator.comparing(TipoDeSensor::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(this::toTipoResponse)
                .toList();
    }

    @Transactional
    public SensorAdminDTOs.TipoSensorResponse criarTipo(SensorAdminDTOs.TipoSensorRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        String nome = request.nome().trim();
        if (tipoRepo.existsById(nome)) {
            throw new ConflictException("TipoDeSensor already exists nome=" + nome);
        }
        return toTipoResponse(tipoRepo.save(new TipoDeSensor(nome)));
    }

    @Transactional
    public SensorAdminDTOs.TipoSensorResponse atualizarTipo(
            String tipoNome,
            SensorAdminDTOs.TipoSensorUpdateRequest request
    ) {
        if (tipoNome == null || tipoNome.isBlank()) {
            throw new IllegalArgumentException("tipoNome is required");
        }
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        String nomeAtual = tipoNome.trim();
        String novoNome = request.nome().trim();
        if (!tipoRepo.existsById(nomeAtual)) {
            throw new NotFoundException("TipoDeSensor not found nome=" + nomeAtual);
        }
        if (nomeAtual.equals(novoNome)) {
            return toTipoResponse(tipoRepo.findById(nomeAtual).orElseThrow());
        }
        if (tipoRepo.existsById(novoNome)) {
            throw new ConflictException("TipoDeSensor already exists nome=" + novoNome);
        }

        entityManager.flush();
        entityManager.createNativeQuery(
                        "update tipo_de_sensor set nome = :novoNome where nome = :nomeAtual")
                .setParameter("novoNome", novoNome)
                .setParameter("nomeAtual", nomeAtual)
                .executeUpdate();
        entityManager.clear();

        TipoDeSensor atualizado = tipoRepo.findById(novoNome)
                .orElseThrow(() -> new IllegalStateException("TipoDeSensor rename failed nome=" + novoNome));
        return toTipoResponse(atualizado);
    }

    @Transactional
    public SensorAdminDTOs.ParametroResponse criarParametro(
            String tipoNome,
            SensorAdminDTOs.ParametroRequest request
    ) {
        if (tipoNome == null || tipoNome.isBlank()) {
            throw new IllegalArgumentException("tipoNome is required");
        }
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        if (request.dataType() == null) {
            throw new IllegalArgumentException("dataType is required");
        }
        TipoDeSensor tipo = tipoRepo.findById(tipoNome.trim())
                .orElseThrow(() -> new NotFoundException("TipoDeSensor not found nome=" + tipoNome));
        String nome = request.nome().trim();
        if (parametroRepo.findByTipo_NomeAndNome(tipo.getNome(), nome).isPresent()) {
            throw new ConflictException("ParametroDef already exists tipo=" + tipo.getNome() + " nome=" + nome);
        }
        String unit = request.dataType() == com.procel.ingestion.entity.sensors.DataType.NUMERIC
                ? blankToNull(request.numericUnit())
                : null;
        ParametroDef parametro = parametroRepo.save(new ParametroDef(
                tipo,
                nome,
                blankToNull(request.descricao()),
                request.dataType(),
                unit
        ));
        return toParametroResponse(parametro);
    }

    @Transactional
    public SensorAdminDTOs.ParametroResponse atualizarParametro(
            UUID parametroId,
            SensorAdminDTOs.ParametroRequest request
    ) {
        if (parametroId == null) {
            throw new IllegalArgumentException("parametroId is required");
        }
        validateParametroRequest(request);
        ParametroDef parametro = parametroRepo.findById(parametroId)
                .orElseThrow(() -> new NotFoundException("ParametroDef not found id=" + parametroId));
        String nome = request.nome().trim();
        parametroRepo.findByTipo_NomeAndNome(parametro.getTipo().getNome(), nome)
                .filter(outro -> !outro.getId().equals(parametroId))
                .ifPresent(outro -> {
                    throw new ConflictException(
                            "ParametroDef already exists tipo=" + parametro.getTipo().getNome() + " nome=" + nome);
                });

        parametro.setNome(nome);
        parametro.setDescricao(blankToNull(request.descricao()));
        parametro.setDataType(request.dataType());
        parametro.setNumericUnit(request.dataType() == com.procel.ingestion.entity.sensors.DataType.NUMERIC
                ? blankToNull(request.numericUnit())
                : null);
        return toParametroResponse(parametroRepo.save(parametro));
    }

    @Transactional
    public SensorAdminDTOs.SensorResponse criarSensor(SensorAdminDTOs.SensorRequest request) {
        if (request == null) throw new IllegalArgumentException("body is required");
        if (request.externalId() == null || request.externalId().isBlank()) {
            throw new IllegalArgumentException("externalId is required");
        }
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        if (request.tipoNome() == null || request.tipoNome().isBlank()) {
            throw new IllegalArgumentException("tipoNome is required");
        }
        if (request.compartimentoId() == null || request.compartimentoId().isBlank()) {
            throw new IllegalArgumentException("compartimentoId is required");
        }
        String externalId = request.externalId().trim();
        if (sensorRepo.existsById(externalId)) {
            throw new ConflictException("Sensor already exists externalId=" + externalId);
        }
        TipoDeSensor tipo = tipoRepo.findById(request.tipoNome().trim())
                .orElseThrow(() -> new NotFoundException("TipoDeSensor not found nome=" + request.tipoNome()));
        Compartimento compartimento = compartimentoRepo.findById(request.compartimentoId().trim())
                .orElseThrow(() -> new NotFoundException(
                        "Compartimento not found id=" + request.compartimentoId()));
        return toSensorResponse(sensorRepo.save(
                new Sensor(externalId, request.nome().trim(), tipo, compartimento)
        ));
    }

    private SensorAdminDTOs.TipoSensorResponse toTipoResponse(TipoDeSensor tipo) {
        List<SensorAdminDTOs.ParametroResponse> parametros =
                parametroRepo.findAllByTipo_Nome(tipo.getNome()).stream()
                        .sorted(Comparator.comparing(ParametroDef::getNome, String.CASE_INSENSITIVE_ORDER))
                        .map(SensorAdminService::toParametroResponse)
                        .toList();
        return new SensorAdminDTOs.TipoSensorResponse(tipo.getNome(), parametros);
    }

    private static SensorAdminDTOs.ParametroResponse toParametroResponse(ParametroDef parametro) {
        return new SensorAdminDTOs.ParametroResponse(
                parametro.getId(),
                parametro.getTipo().getNome(),
                parametro.getNome(),
                parametro.getDescricao(),
                parametro.getDataType(),
                parametro.getNumericUnit()
        );
    }

    private static SensorAdminDTOs.SensorResponse toSensorResponse(Sensor sensor) {
        return new SensorAdminDTOs.SensorResponse(
                sensor.getExternalId(),
                sensor.getNome(),
                sensor.getTipo().getNome(),
                sensor.getCompartimento().getId(),
                sensor.getCompartimento().getNome()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateParametroRequest(SensorAdminDTOs.ParametroRequest request) {
        if (request == null || request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
        if (request.dataType() == null) {
            throw new IllegalArgumentException("dataType is required");
        }
    }
}
