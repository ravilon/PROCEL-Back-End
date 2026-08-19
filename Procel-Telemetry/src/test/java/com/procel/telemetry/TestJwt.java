package com.procel.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestJwt {
    public static final String SECRET = "test-secret-with-at-least-32-characters";
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestJwt() {}

    public static String bearer(String subject, String... roles) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", subject);
            payload.put("email", subject + "@example.test");
            payload.put("roles", List.of(roles));
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", now.plusSeconds(3600).getEpochSecond());

            String unsigned = base64Json(header) + "." + base64Json(payload);
            return "Bearer " + unsigned + "." + sign(unsigned);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String base64Json(Map<String, Object> value) throws Exception {
        return BASE64.encodeToString(OBJECT_MAPPER.writeValueAsBytes(value));
    }

    private static String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return BASE64.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
