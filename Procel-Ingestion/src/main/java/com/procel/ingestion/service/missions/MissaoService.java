package com.procel.ingestion.service.missions;

import com.procel.ingestion.dto.missions.MissaoDTOs;
import com.procel.ingestion.entity.missions.AtividadeStatus;
import com.procel.ingestion.entity.missions.Missao;
import com.procel.ingestion.entity.missions.Atividade;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.missions.MissaoRepository;
import com.procel.ingestion.repository.missions.AtividadeRepository;
import com.procel.ingestion.repository.people.PessoaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MissaoService {

    private final MissaoRepository missaoRepo;
    private final AtividadeRepository atividadeRepo;
    private final PessoaRepository pessoaRepo;

    public MissaoService(MissaoRepository missaoRepo, AtividadeRepository atividadeRepo, PessoaRepository pessoaRepo) {
        this.missaoRepo = missaoRepo;
        this.atividadeRepo = atividadeRepo;
        this.pessoaRepo = pessoaRepo;
    }

    @Transactional
    public MissaoDTOs.MissaoResponse createMissao(MissaoDTOs.CreateMissaoRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.titulo() == null || req.titulo().isBlank()) throw new IllegalArgumentException("titulo is required");

        boolean ativo = req.ativo() == null || req.ativo();
        Missao missao = new Missao(req.titulo().trim(), req.descricao(), req.tipo(), req.value(), ativo);
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
        if (req.tipo() != null) missao.setTipo(req.tipo());
        if (req.value() != null) missao.setValue(req.value());
        if (req.ativo() != null) missao.setAtivo(req.ativo());

        return toMissaoResponse(missao);
    }

    @Transactional
    public void deleteMissao(UUID missaoId) {
        Missao missao = findMissao(missaoId);
        missao.setAtivo(false);

        List<Atividade> atividadesAbertas = atividadeRepo.findByMissaoIdAndStatusIn(
                missaoId,
                List.of(AtividadeStatus.PENDENTE, AtividadeStatus.EM_ANDAMENTO)
        );
        atividadesAbertas.forEach(MissaoService::expireAtividade);
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
        if (atividadeRepo.existsByPessoaIdAndMissaoId(normalizedPessoaId, req.missaoId())) {
            throw new ConflictException("Pessoa already has activity for missaoId=" + req.missaoId());
        }

        Atividade atividade = new Atividade(pessoa, missao, req.status());
        if (req.startedAt() != null) atividade.setStartedAt(req.startedAt());
        normalizeStatusTimestamps(atividade);

        return toAtividadeResponse(atividadeRepo.save(atividade));
    }

    @Transactional(readOnly = true)
    public List<MissaoDTOs.AtividadeResponse> listAtividades(String pessoaId, AtividadeStatus status) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        String normalizedPessoaId = pessoaId.trim();

        if (!pessoaRepo.existsById(normalizedPessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + normalizedPessoaId);
        }

        List<Atividade> atividades = status == null
                ? atividadeRepo.findByPessoaIdOrderByAssignedAtDesc(normalizedPessoaId)
                : atividadeRepo.findByPessoaIdAndStatusOrderByAssignedAtDesc(normalizedPessoaId, status);
        return atividades.stream().map(MissaoService::toAtividadeResponse).toList();
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.AtividadesResumoResponse resumoAtividades(String pessoaId) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        String normalizedPessoaId = pessoaId.trim();

        if (!pessoaRepo.existsById(normalizedPessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + normalizedPessoaId);
        }

        List<Atividade> atividades = atividadeRepo.findByPessoaIdOrderByAssignedAtDesc(normalizedPessoaId);
        return new MissaoDTOs.AtividadesResumoResponse(
                atividades.size(),
                countStatus(atividades, AtividadeStatus.PENDENTE),
                countStatus(atividades, AtividadeStatus.EM_ANDAMENTO),
                countStatus(atividades, AtividadeStatus.CONCLUIDA),
                countStatus(atividades, AtividadeStatus.EXPIRADA),
                countStatus(atividades, AtividadeStatus.CANCELADA)
        );
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.AtividadeResponse getAtividade(String pessoaId, UUID atividadeId) {
        return toAtividadeResponse(findAtividadeForPessoa(pessoaId, atividadeId));
    }

    @Transactional
    public MissaoDTOs.AtividadeResponse updateAtividade(String pessoaId, UUID atividadeId, MissaoDTOs.UpdateAtividadeRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        Atividade atividade = findAtividadeForPessoa(pessoaId, atividadeId);

        if (req.startedAt() != null) atividade.setStartedAt(req.startedAt());
        if (req.completedAt() != null) atividade.setCompletedAt(req.completedAt());
        if (req.status() != null) atividade.setStatus(req.status());
        normalizeStatusTimestamps(atividade);

        return toAtividadeResponse(atividade);
    }

    @Transactional
    public void deleteAtividade(String pessoaId, UUID atividadeId) {
        Atividade atividade = findAtividadeForPessoa(pessoaId, atividadeId);
        expireAtividade(atividade);
    }

    private Missao findMissao(UUID missaoId) {
        if (missaoId == null) throw new IllegalArgumentException("missaoId is required");
        return missaoRepo.findById(missaoId)
                .orElseThrow(() -> new NotFoundException("Missao not found id=" + missaoId));
    }

    private Atividade findAtividadeForPessoa(String pessoaId, UUID atividadeId) {
        if (pessoaId == null || pessoaId.isBlank()) throw new IllegalArgumentException("pessoaId is required");
        if (atividadeId == null) throw new IllegalArgumentException("atividadeId is required");

        String normalizedPessoaId = pessoaId.trim();
        return atividadeRepo.findByIdAndPessoaId(atividadeId, normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException("Atividade not found id=" + atividadeId + " for pessoaId=" + normalizedPessoaId));
    }

    private static void normalizeStatusTimestamps(Atividade atividade) {
        if (atividade.getStatus() == AtividadeStatus.EM_ANDAMENTO && atividade.getStartedAt() == null) {
            atividade.setStartedAt(Instant.now());
        }
        if ((atividade.getStatus() == AtividadeStatus.CONCLUIDA || atividade.getStatus() == AtividadeStatus.EXPIRADA)
                && atividade.getCompletedAt() == null) {
            atividade.setCompletedAt(Instant.now());
        }
    }

    private static void expireAtividade(Atividade atividade) {
        if (atividade.getStatus() == AtividadeStatus.CONCLUIDA) return;

        atividade.setStatus(AtividadeStatus.EXPIRADA);
        if (atividade.getCompletedAt() == null) {
            atividade.setCompletedAt(Instant.now());
        }
    }

    private static long countStatus(List<Atividade> atividades, AtividadeStatus status) {
        return atividades.stream()
                .filter(atividade -> atividade.getStatus() == status)
                .count();
    }

    private static MissaoDTOs.MissaoResponse toMissaoResponse(Missao missao) {
        return new MissaoDTOs.MissaoResponse(
                missao.getId(),
                missao.getTitulo(),
                missao.getDescricao(),
                missao.getTipo(),
                missao.getValue(),
                missao.isAtivo(),
                missao.getCreatedAt()
        );
    }

    private static MissaoDTOs.AtividadeResponse toAtividadeResponse(Atividade atividade) {
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
