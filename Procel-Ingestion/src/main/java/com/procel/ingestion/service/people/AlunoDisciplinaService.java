package com.procel.ingestion.service.people;

import com.procel.ingestion.dto.people.AlunoDisciplinaDTOs;
import com.procel.ingestion.entity.people.AlunoDisciplina;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.entity.rooms.Disciplina;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.people.AlunoDisciplinaRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import com.procel.ingestion.repository.rooms.DisciplinaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AlunoDisciplinaService {

    private static final Pattern PERIODO_LETIVO_PATTERN =
            Pattern.compile("^\\d{4}/[12]$");

    private final AlunoDisciplinaRepository alunoDisciplinaRepo;
    private final PessoaRepository pessoaRepo;
    private final DisciplinaRepository disciplinaRepo;

    public AlunoDisciplinaService(
            AlunoDisciplinaRepository alunoDisciplinaRepo,
            PessoaRepository pessoaRepo,
            DisciplinaRepository disciplinaRepo
    ) {
        this.alunoDisciplinaRepo = alunoDisciplinaRepo;
        this.pessoaRepo = pessoaRepo;
        this.disciplinaRepo = disciplinaRepo;
    }

    @Transactional
    public AlunoDisciplinaDTOs.DisciplinaAlunoResponse vincular(
            String pessoaId,
            AlunoDisciplinaDTOs.VincularDisciplinaRequest req
    ) {
        if (req == null) {
            throw new IllegalArgumentException("body is required");
        }
        if (req.disciplinaId() == null) {
            throw new IllegalArgumentException("disciplinaId is required");
        }

        String normalizedPessoaId = normalizePessoaId(pessoaId);
        String turma = normalizeRequired(req.turma(), "turma");
        String periodoLetivo = normalizePeriodoLetivo(req.periodoLetivo());

        Pessoa pessoa = pessoaRepo.findById(normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException(
                        "Pessoa not found id=" + normalizedPessoaId
                ));
        Disciplina disciplina = disciplinaRepo.findById(req.disciplinaId())
                .orElseThrow(() -> new NotFoundException(
                        "Disciplina not found id=" + req.disciplinaId()
                ));

        if (alunoDisciplinaRepo
                .existsByPessoaIdAndDisciplinaIdAndTurmaAndPeriodoLetivo(
                        normalizedPessoaId,
                        req.disciplinaId(),
                        turma,
                        periodoLetivo
                )) {
            throw new ConflictException(
                    "Pessoa already linked to disciplinaId=" + req.disciplinaId() +
                            ", turma=" + turma +
                            ", periodoLetivo=" + periodoLetivo
            );
        }

        AlunoDisciplina vinculo = new AlunoDisciplina(
                pessoa,
                disciplina,
                turma,
                periodoLetivo,
                req.status()
        );
        return toResponse(alunoDisciplinaRepo.save(vinculo));
    }

    @Transactional(readOnly = true)
    public List<AlunoDisciplinaDTOs.DisciplinaAlunoResponse> listarPorPeriodo(
            String pessoaId,
            String periodoLetivo
    ) {
        String normalizedPessoaId = normalizePessoaId(pessoaId);
        String normalizedPeriodo = normalizePeriodoLetivo(periodoLetivo);

        if (!pessoaRepo.existsById(normalizedPessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + normalizedPessoaId);
        }

        return alunoDisciplinaRepo
                .findByPessoaIdAndPeriodoLetivoOrderByDisciplinaNomeAscTurmaAsc(
                        normalizedPessoaId,
                        normalizedPeriodo
                )
                .stream()
                .map(AlunoDisciplinaService::toResponse)
                .toList();
    }

    @Transactional
    public AlunoDisciplinaDTOs.DisciplinaAlunoResponse atualizar(
            String pessoaId,
            UUID vinculoId,
            AlunoDisciplinaDTOs.AtualizarVinculoRequest req
    ) {
        if (vinculoId == null) {
            throw new IllegalArgumentException("vinculoId is required");
        }
        if (req == null || req.status() == null) {
            throw new IllegalArgumentException("status is required");
        }

        AlunoDisciplina vinculo = findVinculo(pessoaId, vinculoId);
        vinculo.setStatus(req.status());
        return toResponse(vinculo);
    }

    @Transactional
    public void remover(String pessoaId, UUID vinculoId) {
        if (vinculoId == null) {
            throw new IllegalArgumentException("vinculoId is required");
        }
        alunoDisciplinaRepo.delete(findVinculo(pessoaId, vinculoId));
    }

    private AlunoDisciplina findVinculo(String pessoaId, UUID vinculoId) {
        String normalizedPessoaId = normalizePessoaId(pessoaId);
        return alunoDisciplinaRepo.findByIdAndPessoaId(vinculoId, normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException(
                        "AlunoDisciplina not found id=" + vinculoId +
                                " for pessoaId=" + normalizedPessoaId
                ));
    }

    private static String normalizePessoaId(String pessoaId) {
        return normalizeRequired(pessoaId, "pessoaId");
    }

    private static String normalizePeriodoLetivo(String periodoLetivo) {
        String normalized = normalizeRequired(periodoLetivo, "periodoLetivo");
        if (!PERIODO_LETIVO_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "periodoLetivo must use format AAAA/S, for example 2026/1"
            );
        }
        return normalized;
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static AlunoDisciplinaDTOs.DisciplinaAlunoResponse toResponse(
            AlunoDisciplina vinculo
    ) {
        Disciplina disciplina = vinculo.getDisciplina();
        return new AlunoDisciplinaDTOs.DisciplinaAlunoResponse(
                vinculo.getId(),
                vinculo.getPessoa().getId(),
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getUnidadeSigla(),
                vinculo.getTurma(),
                vinculo.getPeriodoLetivo(),
                vinculo.getStatus(),
                vinculo.getVinculadoEm()
        );
    }
}

