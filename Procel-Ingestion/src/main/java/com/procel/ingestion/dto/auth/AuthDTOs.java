package com.procel.ingestion.dto.auth;

import java.time.Instant;
import java.util.Set;

public class AuthDTOs {

    public record LoginRequest(
            String email,
            String password
    ) {}

    public record LoginResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            String userId,
            String email,
            Set<String> roles
    ) {}
}
