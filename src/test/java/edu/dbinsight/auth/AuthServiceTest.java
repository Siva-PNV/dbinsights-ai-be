package edu.dbinsight.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    @Test
    void encryptAndValidatePassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "StrongPass!123";
        String hash = encoder.encode(rawPassword);

        assertNotNull(hash);
        assertTrue(encoder.matches(rawPassword, hash));
        assertEquals(60, hash.length());
    }
}
