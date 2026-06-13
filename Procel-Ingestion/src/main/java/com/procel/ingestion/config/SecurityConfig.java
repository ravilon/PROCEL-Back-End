package com.procel.ingestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        ObjectMapper objectMapper) throws Exception {
                http
                                .cors(Customizer.withDefaults())
                                .csrf(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response,
                                                                authException) -> writeSecurityError(response,
                                                                                objectMapper,
                                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                                "UNAUTHORIZED",
                                                                                "Token ausente ou invalido"))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> writeSecurityError(response,
                                                                                objectMapper,
                                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                                "FORBIDDEN", "Sem permissao")))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(
                                                                "/actuator/health",
                                                                "/api/auth/login",
                                                                "/api/auth/register",
                                                                "/docs",
                                                                "/docs/**",
                                                                "/swagger-ui.html",
                                                                "/swagger-ui/**",
                                                                "/v3/api-docs",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/missoes")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.PUT, "/api/missoes/**")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.DELETE, "/api/missoes/**")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.GET, "/api/missoes/**")
                                                .hasAnyRole("ADMIN", "OPERADOR", "ANALISTA", "USUARIO")
                                                .requestMatchers("/api/pessoas/*/atividades/**")
                                                .hasAnyRole("ADMIN", "OPERADOR", "USUARIO")
                                                .requestMatchers(HttpMethod.POST, "/api/pessoas").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.DELETE, "/api/pessoas/**").hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/api/rooms/sync")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.POST, "/api/rooms/aulas/sync")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.GET, "/api/rooms/aulas/sync/*")
                                                .hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")
                                                .requestMatchers(HttpMethod.POST, "/api/sensors/seed/from-resource")
                                                .hasRole("ADMIN")
                                                .requestMatchers(HttpMethod.POST, "/api/sensors/ingest/mock")
                                                .hasAnyRole("ADMIN", "INGESTOR")
                                                .requestMatchers(HttpMethod.GET, "/api/rules/**")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.POST, "/api/rules/**")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.DELETE, "/api/rules/**")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.GET, "/api/sensors/*/medicoes",
                                                                "/api/sensors/*/medicoes/latest")
                                                .hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")
                                                .requestMatchers(HttpMethod.GET, "/api/rooms/*/medicoes",
                                                                "/api/rooms/*/medicoes/latest")
                                                .hasAnyRole("ADMIN", "OPERADOR", "ANALISTA")
                                                .requestMatchers(HttpMethod.GET, "/api/presencas/abertas/**")
                                                .hasAnyRole("ADMIN", "OPERADOR")
                                                .requestMatchers(HttpMethod.GET, "/api/presencas/ocupacao/**")
                                                .hasAnyRole("ADMIN", "OPERADOR", "ANALISTA", "USUARIO")
                                                .requestMatchers(HttpMethod.POST, "/api/presencas/**")
                                                .hasAnyRole("ADMIN", "OPERADOR", "USUARIO")
                                                .anyRequest().authenticated())
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(
                        @Value("${procel.cors.allowed-origin-patterns:https://procel.servehttp.com,http://localhost:*,http://127.0.0.1:*}") String allowedOriginPatterns) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(splitConfigList(allowedOriginPatterns));
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setExposedHeaders(List.of("Authorization"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        private static List<String> splitConfigList(String value) {
                return Arrays.stream(value.split(","))
                                .map(String::trim)
                                .filter(item -> !item.isBlank())
                                .toList();
        }

        private static void writeSecurityError(
                        HttpServletResponse response,
                        ObjectMapper objectMapper,
                        int status,
                        String error,
                        String message) throws IOException {
                response.setStatus(status);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(), Map.of(
                                "message", message,
                                "error", error,
                                "timestamp", Instant.now()));
        }
}
