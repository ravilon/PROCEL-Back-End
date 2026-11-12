package com.proben.api.security;

/**
 * Componente de password hasher usado pelo modulo API.
 */

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
