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
        if (req == null)
            throw new IllegalArgumentException("body is required");
        if (req.titulo() == null || req.titulo().isBlank())
            throw new IllegalArgumentException("titulo is required");

        boolean ativo = req.ativo() == null || req.ativo();
        Missao missao = new Missao(req.titulo().trim(), req.descricao(), req.tipo(), req.value(), ativo);
        if (req.parentId() != null) {
            missao.setParent(findMissao(req.parentId()));
        }
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
        if (req == null)
            throw new IllegalArgumentException("body is required");
        Missao missao = findMissao(missaoId);

        if (req.titulo() != null && !req.titulo().isBlank())
            missao.setTitulo(req.titulo().trim());
        if (req.descricao() != null)
            missao.setDescricao(req.descricao());
        if (req.tipo() != null)
            missao.setTipo(req.tipo());
        if (req.value() != null)
            missao.setValue(req.value());
        if (req.ativo() != null)
            missao.setAtivo(req.ativo());
        if (req.parentId() != null) {
            Missao parent = findMissao(req.parentId());
            validateParent(missao, parent);
            missao.setParent(parent);
        } else {
            missao.setParent(null);
        }

        return toMissaoResponse(missao);
    }

    @Transactional
    public void deleteMissao(UUID missaoId) {
        Missao missao = findMissao(missaoId);
        missao.setAtivo(false);

        List<Atividade> atividadesAbertas = atividadeRepo.findByMissaoIdAndStatusIn(
                missaoId,
                List.of(AtividadeStatus.PENDENTE, AtividadeStatus.EM_ANDAMENTO));
        atividadesAbertas.forEach(MissaoService::expireAtividade);
    }

    @Transactional
    public MissaoDTOs.AtividadeResponse atribuir(String pessoaId, MissaoDTOs.AtribuirMissaoRequest req) {
        if (req == null)
            throw new IllegalArgumentException("body is required");
        if (req.missaoId() == null)
            throw new IllegalArgumentException("missaoId is required");
        if (pessoaId == null || pessoaId.isBlank())
            throw new IllegalArgumentException("pessoaId is required");

        String normalizedPessoaId = pessoaId.trim();
        Pessoa pessoa = pessoaRepo.findById(normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException("Pessoa not found id=" + normalizedPessoaId));
        Missao missao = findMissao(req.missaoId());
        if (!missao.isAtivo())
            throw new ConflictException("Missao is inactive id=" + req.missaoId());
        if (atividadeRepo.existsByPessoaIdAndMissaoId(normalizedPessoaId, req.missaoId())) {
            throw new ConflictException("Pessoa already has activity for missaoId=" + req.missaoId());
        }

        Atividade atividade = createActivityTree(pessoa, missao, req.status(), req.startedAt(), true);
        return toAtividadeResponse(atividade);
    }

    @Transactional(readOnly = true)
    public List<MissaoDTOs.AtividadeResponse> listAtividades(String pessoaId, AtividadeStatus status) {
        if (pessoaId == null || pessoaId.isBlank())
            throw new IllegalArgumentException("pessoaId is required");
        String normalizedPessoaId = pessoaId.trim();

        if (!pessoaRepo.existsById(normalizedPessoaId)) {
            throw new NotFoundException("Pessoa not found id=" + normalizedPessoaId);
        }

        List<Atividade> atividades = status == null
                ? atividadeRepo.findByPessoaIdOrderByAssignedAtDesc(normalizedPessoaId)
                : atividadeRepo.findByPessoaIdAndStatusOrderByAssignedAtDesc(normalizedPessoaId, status);
        return atividades.stream().map(this::toAtividadeResponse).toList();
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.AtividadesResumoResponse resumoAtividades(String pessoaId) {
        if (pessoaId == null || pessoaId.isBlank())
            throw new IllegalArgumentException("pessoaId is required");
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
                countStatus(atividades, AtividadeStatus.CANCELADA));
    }

    @Transactional(readOnly = true)
    public MissaoDTOs.AtividadeResponse getAtividade(String pessoaId, UUID atividadeId) {
        return toAtividadeResponse(findAtividadeForPessoa(pessoaId, atividadeId));
    }

    @Transactional
    public MissaoDTOs.AtividadeResponse updateAtividade(String pessoaId, UUID atividadeId,
            MissaoDTOs.UpdateAtividadeRequest req) {
        if (req == null)
            throw new IllegalArgumentException("body is required");
        Atividade atividade = findAtividadeForPessoa(pessoaId, atividadeId);

        if (req.startedAt() != null)
            atividade.setStartedAt(req.startedAt());
        if (req.completedAt() != null)
            atividade.setCompletedAt(req.completedAt());
        if (req.status() != null)
            atividade.setStatus(req.status());
        if (hasChildren(atividade.getMissao())) {
            syncActivityFromChildren(atividade);
        } else {
            normalizeStatusTimestamps(atividade);
        }
        syncAncestorActivities(atividade.getPessoa().getId(), atividade.getMissao().getParent());

        return toAtividadeResponse(atividade);
    }

    @Transactional
    public void deleteAtividade(String pessoaId, UUID atividadeId) {
        Atividade atividade = findAtividadeForPessoa(pessoaId, atividadeId);
        expireAtividade(atividade);
    }

    private Missao findMissao(UUID missaoId) {
        if (missaoId == null)
            throw new IllegalArgumentException("missaoId is required");
        return missaoRepo.findById(missaoId)
                .orElseThrow(() -> new NotFoundException("Missao not found id=" + missaoId));
    }

    private static void validateParent(Missao missao, Missao parent) {
        if (missao.getId().equals(parent.getId())) {
            throw new IllegalArgumentException("Missao cannot be its own parent");
        }
        Missao current = parent;
        while (current != null) {
            if (missao.getId().equals(current.getId())) {
                throw new IllegalArgumentException("Mission hierarchy cannot contain cycles");
            }
            current = current.getParent();
        }
    }

    private Atividade findAtividadeForPessoa(String pessoaId, UUID atividadeId) {
        if (pessoaId == null || pessoaId.isBlank())
            throw new IllegalArgumentException("pessoaId is required");
        if (atividadeId == null)
            throw new IllegalArgumentException("atividadeId is required");

        String normalizedPessoaId = pessoaId.trim();
        return atividadeRepo.findByIdAndPessoaId(atividadeId, normalizedPessoaId)
                .orElseThrow(() -> new NotFoundException(
                        "Atividade not found id=" + atividadeId + " for pessoaId=" + normalizedPessoaId));
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
        if (atividade.getStatus() == AtividadeStatus.CONCLUIDA)
            return;

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

    private Atividade createActivityTree(
            Pessoa pessoa,
            Missao missao,
            AtividadeStatus requestedStatus,
            Instant startedAt,
            boolean root
    ) {
        Atividade atividade = atividadeRepo.findByPessoaIdAndMissaoId(pessoa.getId(), missao.getId())
                .orElseGet(() -> atividadeRepo.save(new Atividade(
                        pessoa,
                        missao,
                        root ? requestedStatus : AtividadeStatus.PENDENTE
                )));
        if (root && startedAt != null) {
            atividade.setStartedAt(startedAt);
        }
        for (Missao child : missaoRepo.findByParent_IdOrderByCreatedAtAsc(missao.getId())) {
            if (child.isAtivo()) {
                createActivityTree(pessoa, child, AtividadeStatus.PENDENTE, null, false);
            }
        }
        if (hasChildren(missao)) {
            syncActivityFromChildren(atividade);
        } else {
            normalizeStatusTimestamps(atividade);
        }
        return atividade;
    }

    private boolean hasChildren(Missao missao) {
        return !missaoRepo.findByParent_IdOrderByCreatedAtAsc(missao.getId()).isEmpty();
    }

    private void syncAncestorActivities(String pessoaId, Missao parent) {
        Missao current = parent;
        while (current != null) {
            atividadeRepo.findByPessoaIdAndMissaoId(pessoaId, current.getId())
                    .ifPresent(this::syncActivityFromChildren);
            current = current.getParent();
        }
    }

    private void syncActivityFromChildren(Atividade parentActivity) {
        List<Atividade> children = childActivities(parentActivity);
        if (children.isEmpty()) return;

        boolean allCompleted = children.stream()
                .allMatch(item -> item.getStatus() == AtividadeStatus.CONCLUIDA);
        boolean anyProgress = children.stream()
                .anyMatch(item -> item.getStatus() == AtividadeStatus.CONCLUIDA
                        || item.getStatus() == AtividadeStatus.EM_ANDAMENTO);

        if (allCompleted) {
            parentActivity.setStatus(AtividadeStatus.CONCLUIDA);
            if (parentActivity.getStartedAt() == null) parentActivity.setStartedAt(Instant.now());
            if (parentActivity.getCompletedAt() == null) parentActivity.setCompletedAt(Instant.now());
        } else if (anyProgress) {
            parentActivity.setStatus(AtividadeStatus.EM_ANDAMENTO);
            if (parentActivity.getStartedAt() == null) parentActivity.setStartedAt(Instant.now());
            parentActivity.setCompletedAt(null);
        } else {
            parentActivity.setStatus(AtividadeStatus.PENDENTE);
            parentActivity.setCompletedAt(null);
        }
    }

    private List<Atividade> childActivities(Atividade parentActivity) {
        String pessoaId = parentActivity.getPessoa().getId();
        return missaoRepo.findByParent_IdOrderByCreatedAtAsc(parentActivity.getMissao().getId())
                .stream()
                .map(child -> atividadeRepo.findByPessoaIdAndMissaoId(pessoaId, child.getId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static MissaoDTOs.MissaoResponse toMissaoResponse(Missao missao) {
        return new MissaoDTOs.MissaoResponse(
                missao.getId(),
                missao.getTitulo(),
                missao.getDescricao(),
                missao.getTipo(),
                missao.getValue(),
                missao.isAtivo(),
                missao.getCreatedAt(),
                missao.getParent() == null ? null : missao.getParent().getId(),
                missao.getParent() == null ? null : missao.getParent().getTitulo());
    }

    private MissaoDTOs.AtividadeResponse toAtividadeResponse(Atividade atividade) {
        List<Atividade> children = childActivities(atividade);
        int completed = (int) children.stream()
                .filter(item -> item.getStatus() == AtividadeStatus.CONCLUIDA)
                .count();
        int progress = children.isEmpty()
                ? (atividade.getStatus() == AtividadeStatus.CONCLUIDA ? 100 : 0)
                : completed * 100 / children.size();
        return new MissaoDTOs.AtividadeResponse(
                atividade.getId(),
                atividade.getPessoa().getId(),
                atividade.getPessoa().getNome(),
                atividade.getMissao().getId(),
                atividade.getMissao().getTitulo(),
                atividade.getMissao().getDescricao(),
                atividade.getMissao().getTipo(),
                atividade.getMissao().getValue(),
                atividade.getMissao().getParent() == null ? null : atividade.getMissao().getParent().getId(),
                atividade.getStatus(),
                children.size(),
                completed,
                progress,
                atividade.getAssignedAt(),
                atividade.getStartedAt(),
                atividade.getCompletedAt());
    }
}
