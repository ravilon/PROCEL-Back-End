package com.procel.ingestion.service.people;

import com.procel.ingestion.dto.people.PessoaDTOs;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.exception.ConflictException;
import com.procel.ingestion.exception.NotFoundException;
import com.procel.ingestion.repository.people.PessoaRepository;
import com.procel.ingestion.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PessoaService {

    private final PessoaRepository pessoaRepo;
    private final PasswordHasher passwordHasher;

    public PessoaService(PessoaRepository pessoaRepo, PasswordHasher passwordHasher) {
        this.pessoaRepo = pessoaRepo;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public PessoaDTOs.PessoaResponse create(PessoaDTOs.CreatePessoaRequest req) {
        validateCreate(req);

        String id = req.userId().trim();
        String email = req.email().trim().toLowerCase();
        String matricula = (req.matricula() == null || req.matricula().isBlank()) ? null : req.matricula().trim();

        if (pessoaRepo.existsById(id)) throw new ConflictException("userId already in use: " + id);
        if (pessoaRepo.existsByEmail(email)) throw new ConflictException("email already in use: " + email);
        if (matricula != null && pessoaRepo.existsByMatricula(matricula)) throw new ConflictException("matricula already in use: " + matricula);

        String hash = passwordHasher.hash(req.password());

        Pessoa p = new Pessoa(
                id,
                req.nome().trim(),
                email,
                hash,
                req.telefone(),
                matricula
        );

        p = pessoaRepo.save(p);
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public PessoaDTOs.PessoaResponse get(String id) {
        Pessoa p = pessoaRepo.findById(id).orElseThrow(() -> new NotFoundException("Pessoa not found id=" + id));
        return toResponse(p);
    }

    @Transactional
    public PessoaDTOs.PessoaResponse update(String id, PessoaDTOs.UpdatePessoaRequest req) {
        Pessoa p = pessoaRepo.findById(id).orElseThrow(() -> new NotFoundException("Pessoa not found id=" + id));

        if (req.nome() != null && !req.nome().isBlank()) p.setNome(req.nome().trim());
        if (req.telefone() != null) p.setTelefone(req.telefone());

        if (req.email() != null && !req.email().isBlank()) {
            String email = req.email().trim().toLowerCase();
            pessoaRepo.findByEmail(email)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> { throw new ConflictException("email already in use: " + email); });
            p.setEmail(email);
        }

        // PK: não permitir mudança de userId
        if (req.userId() != null && !req.userId().isBlank() && !req.userId().trim().equals(id)) {
            throw new ConflictException("userId cannot be changed (it is the PK).");
        }

        if (req.matricula() != null && !req.matricula().isBlank()) {
            String matricula = req.matricula().trim();
            pessoaRepo.findByMatricula(matricula)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> { throw new ConflictException("matricula already in use: " + matricula); });
            p.setMatricula(matricula);
        }

        if (req.password() != null && !req.password().isBlank()) {
            p.setPasswordHash(passwordHasher.hash(req.password()));
        }

        return toResponse(p);
    }

    @Transactional
    public void delete(String id) {
        if (!pessoaRepo.existsById(id)) throw new NotFoundException("Pessoa not found id=" + id);
        pessoaRepo.deleteById(id);
    }

    private static void validateCreate(PessoaDTOs.CreatePessoaRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.nome() == null || req.nome().isBlank()) throw new IllegalArgumentException("nome is required");
        if (req.email() == null || req.email().isBlank()) throw new IllegalArgumentException("email is required");
        if (req.userId() == null || req.userId().isBlank()) throw new IllegalArgumentException("userId is required");
        if (req.password() == null || req.password().isBlank()) throw new IllegalArgumentException("password is required");
    }

    private static PessoaDTOs.PessoaResponse toResponse(Pessoa p) {
        return new PessoaDTOs.PessoaResponse(
                p.getId(),
                p.getNome(),
                p.getEmail(),
                p.getTelefone(),
                p.getMatricula(),
                p.getCreatedAt()
        );
    }
}