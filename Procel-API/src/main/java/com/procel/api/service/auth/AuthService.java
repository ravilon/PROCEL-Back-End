package com.procel.api.service.auth;

import com.procel.api.dto.auth.AuthDTOs;
import com.procel.api.dto.people.PessoaDTOs;
import com.procel.api.entity.people.Pessoa;
import com.procel.api.exception.UnauthorizedException;
import com.procel.api.repository.people.PessoaRepository;
import com.procel.api.security.JwtService;
import com.procel.api.security.PasswordHasher;
import com.procel.api.service.people.PessoaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final PessoaRepository pessoaRepo;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final PessoaService pessoaService;

    public AuthService(PessoaRepository pessoaRepo, PasswordHasher passwordHasher, JwtService jwtService, PessoaService pessoaService) {
        this.pessoaRepo = pessoaRepo;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.pessoaService = pessoaService;
    }

    @Transactional(readOnly = true)
    public AuthDTOs.LoginResponse login(AuthDTOs.LoginRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");
        if (req.email() == null || req.email().isBlank()) throw new IllegalArgumentException("email is required");
        if (req.password() == null || req.password().isBlank()) throw new IllegalArgumentException("password is required");

        String email = req.email().trim().toLowerCase();
        Pessoa pessoa = pessoaRepo.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordHasher.matches(req.password(), pessoa.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        JwtService.Token token = jwtService.issue(pessoa);
        Set<String> roles = pessoa.getRoles().stream().map(role -> role.name()).collect(Collectors.toSet());

        return new AuthDTOs.LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                pessoa.getId(),
                pessoa.getEmail(),
                roles
        );
    }

    @Transactional
    public AuthDTOs.RegisterResponse register(AuthDTOs.RegisterRequest req) {
        if (req == null) throw new IllegalArgumentException("body is required");

        PessoaDTOs.PessoaResponse pessoa = pessoaService.registerUsuario(
                req.nome(),
                req.email(),
                req.userId(),
                req.password(),
                req.telefone(),
                req.matricula()
        );

        return new AuthDTOs.RegisterResponse(pessoa);
    }
}
