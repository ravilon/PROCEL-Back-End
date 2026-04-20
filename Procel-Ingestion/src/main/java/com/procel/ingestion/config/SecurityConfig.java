package com.procel.ingestion.config;

import com.procel.ingestion.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/docs",
                                "/docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pessoas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/pessoas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/rooms/sync").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/sensors/seed/from-resource").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/sensors/ingest/mock").hasAnyRole("ADMIN", "INGESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/rules/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.POST, "/api/rules/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/sensors/*/medicoes", "/api/sensors/*/medicoes/latest").hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/rooms/*/medicoes", "/api/rooms/*/medicoes/latest").hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")
                        .requestMatchers(HttpMethod.GET, "/api/presencas/abertas/**").hasAnyRole("ADMIN", "OPERADOR")
                        .requestMatchers(HttpMethod.GET, "/api/presencas/ocupacao/**").hasAnyRole("ADMIN", "OPERADOR", "ANALISTA", "USUARIO")
                        .requestMatchers(HttpMethod.POST, "/api/presencas/**").hasAnyRole("ADMIN", "OPERADOR", "USUARIO")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
