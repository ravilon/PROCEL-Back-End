package com.proben.api.config;

import com.proben.api.entity.people.Pessoa;
import com.proben.api.entity.people.Role;
import com.proben.api.repository.people.PessoaRepository;
import com.proben.api.security.PasswordHasher;
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
            @Value("${proben.security.bootstrap-admin.enabled:false}") boolean enabled,
            @Value("${proben.security.bootstrap-admin.user-id:admin}") String userId,
            @Value("${proben.security.bootstrap-admin.nome:Administrador PROBEN}") String nome,
            @Value("${proben.security.bootstrap-admin.email:admin@proben.local}") String email,
            @Value("${proben.security.bootstrap-admin.password:admin123}") String password
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
