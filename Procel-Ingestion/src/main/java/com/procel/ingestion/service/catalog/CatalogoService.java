package com.procel.ingestion.service.catalog;

import com.procel.ingestion.dto.catalog.CatalogoDTOs;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.entity.people.Role;
import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.entity.rooms.Disciplina;
import com.procel.ingestion.entity.rooms.PeriodoAula;
import com.procel.ingestion.entity.sensors.Sensor;
import com.procel.ingestion.repository.people.PessoaRepository;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import com.procel.ingestion.repository.rooms.DisciplinaRepository;
import com.procel.ingestion.repository.rooms.PeriodoAulaRepository;
import com.procel.ingestion.repository.sensors.SensorRepository;
import com.procel.ingestion.repository.sensors.TipoDeSensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CatalogoService {

    private static final int MAX_RESULTS = 200;

    private final CompartimentoRepository compartimentoRepo;
    private final SensorRepository sensorRepo;
    private final DisciplinaRepository disciplinaRepo;
    private final PeriodoAulaRepository periodoAulaRepo;
    private final TipoDeSensorRepository tipoDeSensorRepo;
    private final PessoaRepository pessoaRepo;

    public CatalogoService(
            CompartimentoRepository compartimentoRepo,
            SensorRepository sensorRepo,
            DisciplinaRepository disciplinaRepo,
            PeriodoAulaRepository periodoAulaRepo,
            TipoDeSensorRepository tipoDeSensorRepo,
            PessoaRepository pessoaRepo
    ) {
        this.compartimentoRepo = compartimentoRepo;
        this.sensorRepo = sensorRepo;
        this.disciplinaRepo = disciplinaRepo;
        this.periodoAulaRepo = periodoAulaRepo;
        this.tipoDeSensorRepo = tipoDeSensorRepo;
        this.pessoaRepo = pessoaRepo;
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.CompartimentoResponse> listarCompartimentos(String query) {
        return compartimentoRepo.findAll().stream()
                .filter(item -> matches(query, item.getId(), item.getNome(), item.getTipo(),
                        item.getPredio().getNome(), item.getUnidade().getNome()))
                .sorted(Comparator.comparing(Compartimento::getNome, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .map(CatalogoService::toCompartimento)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.SensorResponse> listarSensores(String query) {
        return sensorRepo.findAll().stream()
                .filter(item -> matches(query, item.getExternalId(), item.getNome(),
                        item.getTipo().getNome(), item.getCompartimento().getNome()))
                .sorted(Comparator.comparing(Sensor::getNome, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .map(CatalogoService::toSensor)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.SensorResponse> listarSensoresDoCompartimento(String compartimentoId) {
        return sensorRepo.findByCompartimentoIdOrderByNomeAsc(compartimentoId).stream()
                .map(CatalogoService::toSensor)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> listarTiposSensor() {
        return tipoDeSensorRepo.findAll().stream()
                .map(item -> item.getNome())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.DisciplinaResponse> listarDisciplinas(String query) {
        return disciplinaRepo.findAll().stream()
                .filter(item -> matches(query, String.valueOf(item.getId()), item.getNome(), item.getUnidadeSigla()))
                .sorted(Comparator.comparing(Disciplina::getNome, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .map(CatalogoService::toDisciplina)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.PeriodoAulaResponse> periodosDoCompartimento(String compartimentoId) {
        return periodoAulaRepo
                .findTop200ByCompartimentoIdOrderByDataDescTurnoAscPeriodoAulaAsc(compartimentoId)
                .stream()
                .map(CatalogoService::toPeriodoAula)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.PeriodoAulaResponse> periodosDaDisciplina(Long disciplinaId) {
        return periodoAulaRepo
                .findTop200ByDisciplinaIdOrderByDataDescTurnoAscPeriodoAulaAsc(disciplinaId)
                .stream()
                .map(CatalogoService::toPeriodoAula)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogoDTOs.PessoaResumoResponse> listarPessoas(String query) {
        return pessoaRepo.findAll().stream()
                .filter(item -> matches(query, item.getId(), item.getNome(), item.getEmail(), item.getMatricula()))
                .sorted(Comparator.comparing(Pessoa::getNome, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .map(CatalogoService::toPessoa)
                .toList();
    }

    private static boolean matches(String query, String... values) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static CatalogoDTOs.CompartimentoResponse toCompartimento(Compartimento item) {
        return new CatalogoDTOs.CompartimentoResponse(
                item.getId(),
                item.getNome(),
                item.getTipo(),
                item.getPavimento(),
                item.getCapacidade(),
                item.getArea(),
                item.getPredio().getId(),
                item.getPredio().getNome(),
                item.getPredio().getCampus().getNome(),
                item.getUnidade().getNome()
        );
    }

    private static CatalogoDTOs.SensorResponse toSensor(Sensor item) {
        return new CatalogoDTOs.SensorResponse(
                item.getExternalId(),
                item.getNome(),
                item.getTipo().getNome(),
                item.getCompartimento().getId(),
                item.getCompartimento().getNome()
        );
    }

    private static CatalogoDTOs.DisciplinaResponse toDisciplina(Disciplina item) {
        return new CatalogoDTOs.DisciplinaResponse(item.getId(), item.getNome(), item.getUnidadeSigla());
    }

    private static CatalogoDTOs.PeriodoAulaResponse toPeriodoAula(PeriodoAula item) {
        Disciplina disciplina = item.getDisciplina();
        return new CatalogoDTOs.PeriodoAulaResponse(
                item.getId(),
                item.getCompartimento().getId(),
                item.getCompartimento().getNome(),
                disciplina == null ? null : disciplina.getId(),
                disciplina == null ? null : disciplina.getNome(),
                item.getData(),
                item.getTurno(),
                item.getPeriodoAula(),
                item.getHoraInicio(),
                item.getHoraFim(),
                item.getTurma(),
                item.getTipo().name(),
                item.getDescricao(),
                item.getSource(),
                item.getSincronizadoEm()
        );
    }

    private static CatalogoDTOs.PessoaResumoResponse toPessoa(Pessoa item) {
        Set<String> roles = item.getRoles().stream()
                .map(Role::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new CatalogoDTOs.PessoaResumoResponse(
                item.getId(), item.getNome(), item.getEmail(), item.getMatricula(), roles
        );
    }
}
