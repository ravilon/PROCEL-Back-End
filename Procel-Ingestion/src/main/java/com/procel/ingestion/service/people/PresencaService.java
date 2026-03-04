package com.procel.ingestion.service.people;

import com.procel.ingestion.dto.people.PresencaDTOs;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.entity.people.Presenca;
import com.procel.ingestion.entity.rooms.Compartimento;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.people.PessoaRepository;
import com.procel.ingestion.repository.people.PresencaRepository;
import com.procel.ingestion.repository.rooms.CompartimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PresencaService {

    private final PresencaRepository presencaRepo;
    private final PessoaRepository pessoaRepo;
    private final CompartimentoRepository compartimentoRepo;

    public PresencaService(PresencaRepository presencaRepo, PessoaRepository pessoaRepo, CompartimentoRepository compartimentoRepo) {
        this.presencaRepo = presencaRepo;
        this.pessoaRepo = pessoaRepo;
        this.compartimentoRepo = compartimentoRepo;
    }

    @Transactional
    public PresencaDTOs.PresencaResponse checkin(PresencaDTOs.CheckinRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.pessoaId() == null || req.pessoaId().isBlank()) throw new IllegalArgumentException("pessoaId is required");
        if (req.compartimentoId() == null || req.compartimentoId().isBlank()) throw new IllegalArgumentException("compartimentoId is required");

        String pessoaId = req.pessoaId().trim();
        String compartimentoId = req.compartimentoId().trim();

        Pessoa pessoa = pessoaRepo.findById(pessoaId)
                .orElseThrow(() -> new NotFoundException("Pessoa not found id=" + pessoaId));

        Compartimento comp = compartimentoRepo.findById(compartimentoId)
                .orElseThrow(() -> new NotFoundException("Compartimento not found id=" + compartimentoId));

        presencaRepo.findOpenByPessoaId(pessoaId).ifPresent(open -> {
            throw new ConflictException("Pessoa already has an open presence session (presencaId=" + open.getId() + ")");
        });

        Instant checkinAt = (req.checkinAt() != null) ? req.checkinAt() : Instant.now();
        Presenca p = new Presenca(pessoa, comp, checkinAt, req.source());
        p = presencaRepo.save(p);

        return toResponse(p);
    }

    @Transactional
    public PresencaDTOs.PresencaResponse checkout(PresencaDTOs.CheckoutRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.presencaId() == null) throw new IllegalArgumentException("presencaId is required");

        Presenca p = presencaRepo.findById(req.presencaId())
            .orElseThrow(() -> new NotFoundException("Presenca not found id=" + req.presencaId()));

        if (p.getCheckoutAt() != null) throw new ConflictException("Presenca already closed id=" + p.getId());

        Instant checkoutAt = (req.checkoutAt() != null) ? req.checkoutAt() : Instant.now();
        if (checkoutAt.isBefore(p.getCheckinAt())) throw new IllegalArgumentException("checkoutAt must be >= checkinAt");

        p.checkout(checkoutAt);
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<PresencaDTOs.PresencaResponse> abertasPorCompartimento(String compartimentoId) {
        if (compartimentoId == null || compartimentoId.isBlank()) throw new IllegalArgumentException("compartimentoId is required");
        String cid = compartimentoId.trim();
        return presencaRepo.findOpenByCompartimentoId(cid).stream().map(PresencaService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PresencaDTOs.OcupacaoResponse ocupacaoAtual(String compartimentoId) {
        if (compartimentoId == null || compartimentoId.isBlank()) throw new IllegalArgumentException("compartimentoId is required");

        String cid = compartimentoId.trim();

        Compartimento comp = compartimentoRepo.findById(cid)
                .orElseThrow(() -> new NotFoundException("Compartimento not found id=" + cid));

        long presentes = presencaRepo.findOpenByCompartimentoId(cid).size();
        return new PresencaDTOs.OcupacaoResponse(comp.getId(), comp.getNome(), presentes);
    }

    @Transactional
    public PresencaDTOs.PresencaResponse checkoutByPessoa(PresencaDTOs.CheckoutByPessoaRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.pessoaId() == null || req.pessoaId().isBlank()) throw new IllegalArgumentException("pessoaId is required");

        String pessoaId = req.pessoaId().trim();

        Presenca p = presencaRepo.findOpenByPessoaId(pessoaId)
                .orElseThrow(() -> new NotFoundException("No open presence session for pessoaId=" + pessoaId));

        Instant checkoutAt = (req.checkoutAt() != null) ? req.checkoutAt() : Instant.now();
        if (checkoutAt.isBefore(p.getCheckinAt())) throw new IllegalArgumentException("checkoutAt must be >= checkinAt");

        p.checkout(checkoutAt);
        return toResponse(p);
    }

    private static PresencaDTOs.PresencaResponse toResponse(Presenca p) {
        return new PresencaDTOs.PresencaResponse(
                p.getId(),
                p.getPessoa().getId(),       // String
                p.getPessoa().getNome(),
                p.getCompartimento().getId(),
                p.getCompartimento().getNome(),
                p.getCheckinAt(),
                p.getCheckoutAt(),
                p.getSource(),
                p.getCreatedAt()
        );
    }
}