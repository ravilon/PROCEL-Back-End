package com.procel.telemetry.service.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procel.telemetry.config.TelemetryProperties;
import com.procel.telemetry.security.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryServiceJwtIssuerTest {
    @Test
    void issuesShortLivedTelemetryServiceJwtWithFixedRole() throws Exception {
        var properties = new TelemetryProperties();
        properties.getCanonicalWorker().getJwt().setSubject("telemetry-worker");
        properties.getCanonicalWorker().getJwt().setSecret("jwt-test-secret-with-at-least-32-chars");
        properties.getCanonicalWorker().getJwt().setTtl(Duration.ofSeconds(90));

        String bearer = new TelemetryServiceJwtIssuer(new ObjectMapper(), properties).bearerToken();
        String token = bearer.substring("Bearer ".length());

        var claims = new JwtService(new ObjectMapper(), "jwt-test-secret-with-at-least-32-chars").verify(token);
        assertThat(claims.subject()).isEqualTo("telemetry-worker");
        assertThat(claims.roles()).containsExactly("TELEMETRY_SERVICE");

        var payload = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(90);
    }
}
