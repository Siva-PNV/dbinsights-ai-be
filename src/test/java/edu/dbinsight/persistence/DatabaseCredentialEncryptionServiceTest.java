package edu.dbinsight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DatabaseCredentialEncryptionServiceTest {
    @Test
    void encryptsAndDecryptsCredentialsWithoutStoringPlaintext() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        DatabaseCredentialEncryptionService service = new DatabaseCredentialEncryptionService(key);

        String encrypted = service.encrypt("monitor_user", "sensitive-password");
        DatabaseCredentialEncryptionService.Credentials decrypted = service.decrypt(encrypted);

        assertNotEquals("monitor_user:sensitive-password", encrypted);
        assertEquals("monitor_user", decrypted.username());
        assertEquals("sensitive-password", decrypted.password());
    }

    @Test
    void rejectsKeysThatAreNotAes256Material() {
        DatabaseCredentialEncryptionService service = new DatabaseCredentialEncryptionService(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThrows(IllegalStateException.class, () -> service.encrypt("monitor_user", "password"));
    }
}