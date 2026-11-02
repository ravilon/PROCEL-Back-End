package com.procel.api.service.shared;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CredentialEncryptionServiceTest {
    @Test void encryptsWithRandomIvAndRejectsWrongMasterKey() {
        UUID id=UUID.randomUUID();CredentialEncryptionService first=service((byte)1),second=service((byte)2);
        var a=first.encrypt(id,"provider-secret");var b=first.encrypt(id,"provider-secret");
        assertThat(a.iv()).isNotEqualTo(b.iv());assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
        assertThat(first.decrypt(id,a.ciphertext(),a.iv())).isEqualTo("provider-secret");
        assertThatThrownBy(()->second.decrypt(id,a.ciphertext(),a.iv())).hasMessageContaining("cannot be decrypted");
    }
    @Test void missingMasterKeyBlocksCredentialStorage() {
        var service=new CredentialEncryptionService(new MockEnvironment());service.initialize();
        assertThat(service.available()).isFalse();
        assertThatThrownBy(()->service.encrypt(UUID.randomUUID(),"secret")).hasMessageContaining("PROCEL_CREDENTIAL_ENCRYPTION_KEY");
    }
    @Test void invalidConfiguredMasterKeyFailsInitialization() {
        var service=new CredentialEncryptionService(new MockEnvironment().withProperty("PROCEL_CREDENTIAL_ENCRYPTION_KEY","too-short"));
        assertThatThrownBy(service::initialize).hasMessageContaining("exactly 32");
    }
    private static CredentialEncryptionService service(byte fill){byte[] key=new byte[32];java.util.Arrays.fill(key,fill);var value=new CredentialEncryptionService(new MockEnvironment().withProperty("PROCEL_CREDENTIAL_ENCRYPTION_KEY",Base64.getEncoder().encodeToString(key)));value.initialize();return value;}
}
