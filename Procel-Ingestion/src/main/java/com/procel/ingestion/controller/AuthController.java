package com.procel.ingestion.controller;

import com.procel.ingestion.dto.auth.AuthDTOs;
import com.procel.ingestion.service.auth.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthDTOs.LoginResponse login(@RequestBody AuthDTOs.LoginRequest req) {
        return authService.login(req);
    }
}
