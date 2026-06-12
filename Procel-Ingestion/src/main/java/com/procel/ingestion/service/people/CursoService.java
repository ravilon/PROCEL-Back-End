package com.procel.ingestion.service.people;

import com.procel.ingestion.dto.people.CursoDTOs;
import com.procel.ingestion.entity.people.Curso;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.people.CursoRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CursoService {

    private final CursoRepository cursoRepo;
    private final PessoaRepository pessoaRepo;

    public CursoService(CursoRepository cursoRepo, PessoaRepository pessoaRepo) {
        this.cursoRepo = cursoRepo;
        this.pessoaRepo = pessoaRepo;
    }

    @Transactional(readOnly = true)
    public List<CursoDTOs.CursoResponse> listar(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return cursoRepo.findAll().stream()
                .filter(item -> normalized.isBlank()
                        || item.getNome().toLowerCase(Locale.ROOT).contains(normalized)
                        || (item.getUnidadeSigla() != null
                        && item.getUnidadeSigla().toLowerCase(Locale.ROOT).contains(normalized))
                        || String.valueOf(item.getId()).contains(normalized))
                .sorted(Comparator.comparing(Curso::getNome, String.CASE_INSENSITIVE_ORDER))
                .limit(200)
                .map(CursoService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CursoDTOs.CursoResponse buscar(Long id) {
        return toResponse(findCurso(id));
    }

    @Transactional
    public CursoDTOs.CursoResponse criar(CursoDTOs.CursoRequest request) {
        validate(request);
        Curso curso = new Curso(request.nome().trim(), normalizeOptional(request.unidadeSigla()));
        return toResponse(cursoRepo.save(curso));
    }

    @Transactional
    public CursoDTOs.CursoResponse atualizar(Long id, CursoDTOs.CursoRequest request) {
        validate(request);
        Curso curso = findCurso(id);
        curso.setNome(request.nome().trim());
        curso.setUnidadeSigla(normalizeOptional(request.unidadeSigla()));
        return toResponse(curso);
    }

    @Transactional
    public void remover(Long id) {
        Curso curso = findCurso(id);
        if (pessoaRepo.existsByCursoId(id)) {
            throw new ConflictException("Curso possui pessoas vinculadas id=" + id);
        }
        cursoRepo.delete(curso);
    }

    @Transactional(readOnly = true)
    public CursoDTOs.PessoaCursoResponse cursoDaPessoa(String pessoaId) {
        return toPessoaCurso(findPessoa(pessoaId));
    }

    @Transactional
    public CursoDTOs.PessoaCursoResponse vincular(String pessoaId, Long cursoId) {
        Pessoa pessoa = findPessoa(pessoaId);
        pessoa.setCurso(findCurso(cursoId));
        return toPessoaCurso(pessoa);
    }

    @Transactional
    public CursoDTOs.PessoaCursoResponse removerVinculo(String pessoaId) {
        Pessoa pessoa = findPessoa(pessoaId);
        pessoa.setCurso(null);
        return toPessoaCurso(pessoa);
    }

    private Curso findCurso(Long id) {
        if (id == null) throw new IllegalArgumentException("cursoId is required");
        return cursoRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Curso not found id=" + id));
    }

    private Pessoa findPessoa(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        return pessoaRepo.findById(id.trim())
                .orElseThrow(() -> new NotFoundException("Pessoa not found id=" + id));
    }

    private static void validate(CursoDTOs.CursoRequest request) {
        if (request == null) throw new IllegalArgumentException("body is required");
        if (request.nome() == null || request.nome().isBlank()) {
            throw new IllegalArgumentException("nome is required");
        }
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static CursoDTOs.CursoResponse toResponse(Curso curso) {
        return new CursoDTOs.CursoResponse(curso.getId(), curso.getNome(), curso.getUnidadeSigla());
    }

    private static CursoDTOs.PessoaCursoResponse toPessoaCurso(Pessoa pessoa) {
        return new CursoDTOs.PessoaCursoResponse(
                pessoa.getId(),
                pessoa.getNome(),
                pessoa.getCurso() == null ? null : toResponse(pessoa.getCurso())
        );
    }
}
