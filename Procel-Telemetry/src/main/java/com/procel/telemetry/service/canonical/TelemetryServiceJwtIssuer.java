package com.procel.telemetry.service.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelemetryServiceJwtIssuer {
    static final String ROLE = "TELEMETRY_SERVICE";

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper objectMapper;
    private final TelemetryProperties properties;

    public TelemetryServiceJwtIssuer(ObjectMapper objectMapper, TelemetryProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String bearerToken() {
        var jwt = properties.getCanonicalWorker().getJwt();
        if (jwt.getSecret() == null || jwt.getSecret().length() < 32) {
            throw new IllegalStateException("procel.telemetry.canonical-worker.jwt.secret must have at least 32 characters");
        }
        if (jwt.getTtl() == null || jwt.getTtl().isZero() || jwt.getTtl().isNegative()) {
            throw new IllegalStateException("procel.telemetry.canonical-worker.jwt.ttl must be positive");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("procel.telemetry.canonical-worker.jwt.subject is required");
        }

        Instant now = Instant.now();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject.trim());
        payload.put("email", subject.trim() + "@service.procel.local");
        payload.put("roles", List.of(ROLE));
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(jwt.getTtl()).getEpochSecond());

        String unsigned = base64Json(header) + "." + base64Json(payload);
        return "Bearer " + unsigned + "." + sign(unsigned, jwt.getSecret());
    }

    private String base64Json(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize service token", ex);
        }
    }

    private String sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign service token", ex);
        }
    }

    static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
