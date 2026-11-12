package com.proben.api.service.shared;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Servico que implementa as operacoes de negocio de credential encryption.
 */
@Service
public class CredentialEncryptionService {
    static final String MASTER_KEY_NAME = "PROBEN_CREDENTIAL_ENCRYPTION_KEY";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec masterKey;

    public CredentialEncryptionService(Environment environment) { this.environment = environment; }

    @PostConstruct
    void initialize() {
        String configured = environment.getProperty(MASTER_KEY_NAME);
        if (configured == null || configured.isBlank()) return;
        final byte[] decoded;
        try { decoded = Base64.getDecoder().decode(configured.trim()); }
        catch (IllegalArgumentException ex) { throw invalidKey(); }
        if (decoded.length != 32) throw invalidKey();
        masterKey = new SecretKeySpec(decoded, "AES");
    }

    public boolean available() { return masterKey != null; }

    public EncryptedCredential encrypt(UUID integrationId, String plaintext) {
        if (!available()) throw new IllegalStateException(MASTER_KEY_NAME + " is required to store managed credentials");
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("credential must not be blank");
        byte[] iv = new byte[IV_BYTES]; random.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, integrationId, iv);
            return new EncryptedCredential(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)), iv);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("managed credential encryption failed", ex);
        }
    }

    public String decrypt(UUID integrationId, byte[] ciphertext, byte[] iv) {
        if (ciphertext == null || iv == null) return null;
        if (!available()) throw new IllegalStateException(MASTER_KEY_NAME + " is required to decrypt managed credentials");
        if (iv.length != IV_BYTES) throw new IllegalStateException("managed credential is invalid");
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, integrationId, iv);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new IllegalStateException("managed credential cannot be decrypted with the configured master key");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("managed credential decryption failed", ex);
        }
    }

    private Cipher cipher(int mode, UUID integrationId, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, masterKey, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(integrationId.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static IllegalStateException invalidKey() {
        return new IllegalStateException(MASTER_KEY_NAME + " must be Base64 encoding of exactly 32 random bytes");
    }

    public record EncryptedCredential(byte[] ciphertext, byte[] iv) {}
}
