package com.procel.ingestion.service.missions;

import com.procel.ingestion.dto.missions.MissaoDTOs;
import com.procel.ingestion.entity.missions.AtividadeStatus;
import com.procel.ingestion.entity.missions.Missao;
import com.procel.ingestion.entity.missions.PessoaMissao;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.missions.MissaoRepository;
import com.procel.ingestion.repository.missions.PessoaMissaoRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MissaoService {

    private final MissaoRepository missaoRepo;
    private final PessoaMissaoRepository pessoaMissaoRepo;
    private final PessoaRepository pessoaRepo;

    public MissaoService(MissaoRepository missaoRepo, PessoaMissaoRepository pessoaMissaoRepo, PessoaRepository pessoaRepo) {
        this.missaoRepo = missaoRepo;
        this.pessoaMissaoRepo = pessoaMissaoRepo;
        this.pessoaRepo = pessoaRepo;
    }

    @Transactional
    public MissaoDTOs.MissaoResponse createMissao(MissaoDTOs.CreateMissaoRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.titulo() == null || req.titulo().isBlank()) throw new IllegalArgumentException("titulo is required");

        boolean ativo = req.ativo() == null || req.ativo();
        Missao missao = new Missao(req.titulo().trim(), req.descricao(), ativo);
        return toMissaoResponse(missaoRepo.save(missao));
    }

    @Transactional(readOnly = true)
    public List<MissaoDTOs.MissaoResponse> listMissoes(Boolean ativo) {
        List<Missao> missoes = ativo == null
                ? missaoRepo.findAllByOrderByCreatedAtDesc()
                : missaoRepo.findByAtivoOrderByCreatedAtDesc(ativo);
        return missoes.stream().map(MissaoService::toMissaoResponse).toList();
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.MissaoResponse getMissao(UUID missaoId) {
        return toMissaoResponse(findMissao(missaoId));
    }

    @Transactional
    public MissaoDTOs.MissaoResponse updateMissao(UUID missaoId, MissaoDTOs.UpdateMissaoRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        Missao missao = findMissao(missaoId);

        if (req.titulo() != null && !req.titulo().isBlank()) missao.setTitulo(req.titulo().trim());
        if (req.descricao() != null) missao.setDescricao(req.descricao());
        if (req.ativo() != null) missao.setAtivo(req.ativo());

        return toMissaoResponse(missao);
    }

    @Transactional
    public void deleteMissao(UUID missaoId) {
        Missao missao = findMissao(missaoId);
        missaoRepo.delete(missao);
    }

    @Transactional
    public MissaoDTOs.AtividadeResponse atribuir(String pessoaId, MissaoDTOs.AtribuirMissaoRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.missaoId() == null) throw new IllegalArgumentException("missaoId is required");
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");

        String normalizedPessoaId = pessoaId.trim();
        Pessoa pessoa = pessoaRepo.findById(normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException("Pessoa not found id=" + normalizedPessoaId));
        Missao missao = findMissao(req.missaoId());
        if (!missao.isAtivo()) throw new ConflictException("Missao is inactive id=" + req.missaoId());
        if (pessoaMissaoRepo.existsByPessoaIdAndMissaoId(normalizedPessoaId, req.missaoId())) {
            throw new ConflictException("Pessoa already has activity for missaoId=" + req.missaoId());
        }

        PessoaMissao atividade = new PessoaMissao(pessoa, missao, req.status());
        if (req.startedAt() != null) atividade.setStartedAt(req.startedAt());
        normalizeStatusTimestamps(atividade);

        return toAtividadeResponse(pessoaMissaoRepo.save(atividade));
    }

    @Transactional(readOnly = true)
    public List<MissaoDTOs.AtividadeResponse> listAtividades(String pessoaId, AtividadeStatus status) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        String normalizedPessoaId = pessoaId.trim();

        if (!pessoaRepo.existsById(normalizedPessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + normalizedPessoaId);
        }

        List<PessoaMissao> atividades = status == null
                ? pessoaMissaoRepo.findByPessoaIdOrderByAssignedAtDesc(normalizedPessoaId)
                : pessoaMissaoRepo.findByPessoaIdAndStatusOrderByAssignedAtDesc(normalizedPessoaId, status);
        return atividades.stream().map(MissaoService::toAtividadeResponse).toList();
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.AtividadeResponse getAtividade(String pessoaId, UUID atividadeId) {
        return toAtividadeResponse(findAtividadeForPessoa(pessoaId, atividadeId));
    }

    @Transactional
    public MissaoDTOs.AtividadeResponse updateAtividade(String pessoaId, UUID atividadeId, MissaoDTOs.UpdateAtividadeRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        PessoaMissao atividade = findAtividadeForPessoa(pessoaId, atividadeId);

        if (req.startedAt() != null) atividade.setStartedAt(req.startedAt());
        if (req.completedAt() != null) atividade.setCompletedAt(req.completedAt());
        if (req.status() != null) atividade.setStatus(req.status());
        normalizeStatusTimestamps(atividade);

        return toAtividadeResponse(atividade);
    }

    @Transactional
    public void deleteAtividade(String pessoaId, UUID atividadeId) {
        PessoaMissao atividade = findAtividadeForPessoa(pessoaId, atividadeId);
        pessoaMissaoRepo.delete(atividade);
    }

    private Missao findMissao(UUID missaoId) {
        if (missaoId == null) throw new IllegalArgumentException("missaoId is required");
        return missaoRepo.findById(missaoId)
                .orElseThrow(() -> new NotFoundException("Missao not found id=" + missaoId));
    }

    private PessoaMissao findAtividadeForPessoa(String pessoaId, UUID atividadeId) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");

        String normalizedPessoaId = pessoaId.trim();
        return pessoaMissaoRepo.findByIdAndPessoaId(atividadeId, normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException("Atividade not found id=" + atividadeId + " for pessoaId=" + normalizedPessoaId));
    }

    private static void normalizeStatusTimestamps(PessoaMissao atividade) {
        if (atividade.getStatus() == AtividadeStatus.EM_ANDAMENTO && atividade.getStartedAt() == null) {
            atividade.setStartedAt(Instant.now());
        }
        if (atividade.getStatus() == AtividadeStatus.CONCLUIDA && atividade.getCompletedAt() == null) {
            atividade.setCompletedAt(Instant.now());
        }
    }

    private static MissaoDTOs.MissaoResponse toMissaoResponse(Missao missao) {
        return new MissaoDTOs.MissaoResponse(
                missao.getId(),
                missao.getTitulo(),
                missao.getDescricao(),
                missao.isAtivo(),
                missao.getCreatedAt()
        );
    }

    private static MissaoDTOs.AtividadeResponse toAtividadeResponse(PessoaMissao atividade) {
        return new MissaoDTOs.AtividadeResponse(
                atividade.getId(),
                atividade.getPessoa().getId(),
                atividade.getPessoa().getNome(),
                atividade.getMissao().getId(),
                atividade.getMissao().getTitulo(),
                atividade.getMissao().getDescricao(),
                atividade.getStatus(),
                atividade.getAssignedAt(),
                atividade.getStartedAt(),
                atividade.getCompletedAt()
        );
    }
}
