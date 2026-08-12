package com.procel.api.config;

import com.procel.api.entity.people.Pessoa;
import com.procel.api.entity.people.Role;
import com.procel.api.repository.people.PessoaRepository;
import com.procel.api.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
public class SecurityBootstrapConfig {

    @Bean
    CommandLineRunner bootstrapAdmin(
            PessoaRepository pessoaRepo,
            PasswordHasher passwordHasher,
            @Value("${procel.security.bootstrap-admin.enabled:false}") boolean enabled,
            @Value("${procel.security.bootstrap-admin.user-id:admin}") String userId,
            @Value("${procel.security.bootstrap-admin.nome:Administrador PROCEL}") String nome,
            @Value("${procel.security.bootstrap-admin.email:admin@procel.local}") String email,
            @Value("${procel.security.bootstrap-admin.password:admin123}") String password
    ) {
        return args -> {
            if (!enabled) return;

            String normalizedEmail = email.trim().toLowerCase();
            if (pessoaRepo.findByEmail(normalizedEmail).isPresent()) return;

            Pessoa admin = new Pessoa(
                    userId.trim(),
                    nome.trim(),
                    normalizedEmail,
                    passwordHasher.hash(password),
                    null,
                    null
            );
            admin.setRoles(new LinkedHashSet<>(Set.of(Role.ADMIN, Role.OPERADOR, Role.ANALISTA, Role.USUARIO, Role.INGESTOR)));
            pessoaRepo.save(admin);
        };
    }
}
