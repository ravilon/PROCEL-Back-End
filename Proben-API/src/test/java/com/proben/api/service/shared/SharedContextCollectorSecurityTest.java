package com.proben.api.service.shared;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedContextCollectorSecurityTest {
    @Test
    void blocksNonHttpPrivateAndCredentialBearingTargets() {
        assertThatThrownBy(() -> SharedContextCollector.validateTarget(URI.create("file:///etc/passwd"), false))
                .hasMessageContaining("allowed HTTP");
        assertThatThrownBy(() -> SharedContextCollector.validateTarget(URI.create("http://10.0.0.1/weather"), false))
                .hasMessageContaining("non-public");
        assertThatThrownBy(() -> SharedContextCollector.validateTarget(URI.create("http://user:secret@example.com/weather"), false))
                .hasMessageContaining("allowed HTTP");
        assertThatThrownBy(() -> SharedContextCollector.validateTarget(URI.create("http://127.0.0.1/weather"), false))
                .hasMessageContaining("non-public");
        assertThatCode(() -> SharedContextCollector.validateTarget(URI.create("http://127.0.0.1/weather"), true))
                .doesNotThrowAnyException();
    }
}
