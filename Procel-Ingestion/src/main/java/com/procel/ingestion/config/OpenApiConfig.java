package com.procel.ingestion.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI procelIngestionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PROCEL Ingestion API")
                        .version("0.0.1")
                        .description("""
                                API para sincronizacao de salas, cadastro de pessoas, controle de presencas,
                                catalogo de missoes, atividades, ingestao mockada de sensores,
                                consulta de medicoes e qualificacao de parametros.

                                Missoes sao catalogo versionado: remover uma missao depreca o modelo
                                (ativo=false) sem apagar historico. Atividades tambem preservam
                                historico: remover uma atividade marca status EXPIRADA. Use o resumo de
                                atividades para acompanhar quantas foram concluidas, expiradas, canceladas
                                ou ainda estao abertas.

                                O schema e os seeds de catalogo sao aplicados automaticamente por Flyway
                                em classpath:db/migration.

                                Autenticacao: use POST /api/auth/login para obter um accessToken e informe
                                o valor no botao Authorize como Bearer JWT.
                                """)
                        .contact(new Contact().name("PROCEL"))
                        .license(new License().name("Internal")))
                .servers(List.of(new Server().url("/").description("Servidor atual")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
