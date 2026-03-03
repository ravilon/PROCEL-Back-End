package com.procel.ingestion.security;

public interface PasswordHasher {
    String hash(String rawPassword);
}