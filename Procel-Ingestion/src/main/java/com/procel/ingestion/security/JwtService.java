package com.procel.ingestion.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.ingestion.entity.people.Pessoa;
import com.procel.ingestion.entity.people.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationMinutes;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${procel.security.jwt.secret}") String secret,
            @Value("${procel.security.jwt.expiration-minutes:60}") long expirationMinutes
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("procel.security.jwt.secret must have at least 32 characters");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMinutes = expirationMinutes;
    }

    public Token issue(Pessoa pessoa) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationMinutes * 60);

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", pessoa.getId());
        payload.put("email", pessoa.getEmail());
        payload.put("roles", pessoa.getRoles().stream().map(Role::name).sorted().toList());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String unsigned = base64Json(header) + "." + base64Json(payload);
        String signature = sign(unsigned);
        return new Token(unsigned + "." + signature, expiresAt);
    }

    public Claims verify(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token is required");

        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("invalid token");

        String unsigned = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsigned);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        String subject = stringClaim(payload, "sub");
        String email = stringClaim(payload, "email");
        long exp = numberClaim(payload, "exp");
        if (Instant.now().isAfter(Instant.ofEpochSecond(exp))) {
            throw new IllegalArgumentException("token expired");
        }

        Object rolesValue = payload.get("roles");
        Set<String> roles = new LinkedHashSet<>();
        if (rolesValue instanceof Collection<?> values) {
            roles = values.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        return new Claims(subject, email, roles);
    }

    private String base64Json(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize token", e);
        }
    }

    private Map<String, Object> decodeJson(String encoded) {
        try {
            byte[] json = BASE64_URL_DECODER.decode(encoded);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid token payload", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign token", e);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private static String stringClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("missing claim: " + name);
        return value.toString();
    }

    private static long numberClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof Number n) return n.longValue();
        throw new IllegalArgumentException("missing numeric claim: " + name);
    }

    public record Token(String value, Instant expiresAt) {}
    public record Claims(String subject, String email, Set<String> roles) {}

}
