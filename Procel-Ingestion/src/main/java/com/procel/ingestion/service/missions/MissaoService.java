package com.procel.ingestion.service.missions;

import com.procel.ingestion.dto.missions.MissaoDTOs;
import com.procel.ingestion.entity.missions.Missao;
import com.procel.ingestion.entity.missions.MissaoStatus;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.missions.MissaoRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MissaoService {

    private final MissaoRepository missaoRepo;
    private final PessoaRepository pessoaRepo;

    public MissaoService(MissaoRepository missaoRepo, PessoaRepository pessoaRepo) {
        this.missaoRepo = missaoRepo;
        this.pessoaRepo = pessoaRepo;
    }

    @Transactional
    public MissaoDTOs.MissaoResponse create(String pessoaId, MissaoDTOs.CreateMissaoRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        if (req.titulo() == null || req.titulo().isBlank()) throw new IllegalArgumentException("titulo is required");

        String normalizedPessoaId = pessoaId.trim();
        Pessoa pessoa = pessoaRepo.findById(normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException("Pessoa not found id=" + normalizedPessoaId));

        Missao missao = new Missao(pessoa, req.titulo().trim(), req.descricao());
        if (req.status() != null) missao.setStatus(req.status());
        if (req.startedAt() != null) missao.setStartedAt(req.startedAt());
        if (missao.getStatus() == MissaoStatus.CONCLUIDA && missao.getCompletedAt() == null) {
            missao.setCompletedAt(Instant.now());
        }

        return toResponse(missaoRepo.save(missao));
    }

    @Transactional(readOnly = true)
    public List<MissaoDTOs.MissaoResponse> list(String pessoaId, MissaoStatus status) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        String normalizedPessoaId = pessoaId.trim();

        if (!pessoaRepo.existsById(normalizedPessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + normalizedPessoaId);
        }

        List<Missao> missoes = status == null
                ? missaoRepo.findByPessoaIdOrderByCreatedAtDesc(normalizedPessoaId)
                : missaoRepo.findByPessoaIdAndStatusOrderByCreatedAtDesc(normalizedPessoaId, status);

        return missoes.stream().map(MissaoService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.MissaoResponse get(String pessoaId, UUID missaoId) {
        return toResponse(findForPessoa(pessoaId, missaoId));
    }

    @Transactional
    public MissaoDTOs.MissaoResponse update(String pessoaId, UUID missaoId, MissaoDTOs.UpdateMissaoRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");

        Missao missao = findForPessoa(pessoaId, missaoId);

        if (req.titulo() != null && !req.titulo().isBlank()) missao.setTitulo(req.titulo().trim());
        if (req.descricao() != null) missao.setDescricao(req.descricao());
        if (req.startedAt() != null) missao.setStartedAt(req.startedAt());
        if (req.completedAt() != null) missao.setCompletedAt(req.completedAt());
        if (req.status() != null) {
            missao.setStatus(req.status());
            if (req.status() == MissaoStatus.EM_ANDAMENTO && missao.getStartedAt() == null) {
                missao.setStartedAt(Instant.now());
            }
            if (req.status() == MissaoStatus.CONCLUIDA && missao.getCompletedAt() == null) {
                missao.setCompletedAt(Instant.now());
            }
        }

        return toResponse(missao);
    }

    @Transactional
    public void delete(String pessoaId, UUID missaoId) {
        Missao missao = findForPessoa(pessoaId, missaoId);
        missaoRepo.delete(missao);
    }

    private Missao findForPessoa(String pessoaId, UUID missaoId) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        if (missaoId == null) throw new IllegalArgumentException("missaoId is required");

        String normalizedPessoaId = pessoaId.trim();
        return missaoRepo.findByIdAndPessoaId(missaoId, normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException("Missao not found id=" + missaoId + " for pessoaId=" + normalizedPessoaId));
    }

    private static MissaoDTOs.MissaoResponse toResponse(Missao missao) {
        return new MissaoDTOs.MissaoResponse(
                missao.getId(),
                missao.getPessoa().getId(),
                missao.getPessoa().getNome(),
                missao.getTitulo(),
                missao.getDescricao(),
                missao.getStatus(),
                missao.getCreatedAt(),
                missao.getStartedAt(),
                missao.getCompletedAt()
        );
    }
}
