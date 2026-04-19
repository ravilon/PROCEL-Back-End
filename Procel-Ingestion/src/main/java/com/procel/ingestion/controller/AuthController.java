package com.procel.ingestion.controller;

import com.procel.ingestion.dto.auth.AuthDTOs;
import com.procel.ingestion.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Autenticacao e emissao de JWT.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autentica usuario e emite JWT",
            description = "Valida email/senha e retorna um accessToken. Use esse token no botao Authorize da documentacao."
    )
    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso.")
    @ApiResponse(responseCode = "400", description = "Body, email ou password ausente.")
    @ApiResponse(responseCode = "401", description = "Credenciais invalidas.")
    public AuthDTOs.LoginResponse login(@RequestBody AuthDTOs.LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/register")
    @Operation(
            summary = "Realiza auto cadastro de usuario comum",
            description = "Endpoint publico para criar uma conta comum. O backend sempre atribui a role USUARIO e nao aceita roles do cliente."
    )
    @ApiResponse(responseCode = "200", description = "Usuario cadastrado com sucesso.")
    @ApiResponse(responseCode = "400", description = "Dados obrigatorios ausentes ou invalidos.")
    @ApiResponse(responseCode = "409", description = "userId, email ou matricula ja cadastrado.")
    public AuthDTOs.RegisterResponse register(@RequestBody AuthDTOs.RegisterRequest req) {
        return authService.register(req);
    }
}
