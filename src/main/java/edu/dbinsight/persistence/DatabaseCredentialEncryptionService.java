package edu.dbinsight.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatabaseCredentialEncryptionService {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String encodedKey;

    public DatabaseCredentialEncryptionService(
            @Value("${app.security.database-credentials-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String username, String password) {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        ByteBuffer plaintext = ByteBuffer.allocate(Integer.BYTES + usernameBytes.length + passwordBytes.length);
        plaintext.putInt(usernameBytes.length).put(usernameBytes).put(passwordBytes);

        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.array());
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt database credentials.", exception);
        }
    }

    public Credentials decrypt(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new IllegalStateException("Stored database credentials have an unsupported format.");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            ByteBuffer plaintext = ByteBuffer.wrap(cipher.doFinal(encrypted));
            int usernameLength = plaintext.getInt();
            if (usernameLength < 0 || usernameLength > plaintext.remaining()) {
                throw new IllegalStateException("Stored database credentials are invalid.");
            }
            byte[] username = new byte[usernameLength];
            byte[] password = new byte[plaintext.remaining() - usernameLength];
            plaintext.get(username).get(password);
            return new Credentials(new String(username, StandardCharsets.UTF_8),
                    new String(password, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt database credentials. Verify the configured encryption key.", exception);
        }
    }

    private SecretKeySpec key() {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                Arrays.fill(key, (byte) 0);
                throw new IllegalStateException("DBINSIGHT_DATABASE_CREDENTIALS_KEY must be base64-encoded 32-byte key material.");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DBINSIGHT_DATABASE_CREDENTIALS_KEY must be base64-encoded 32-byte key material.", exception);
        }
    }

    public record Credentials(String username, String password) {}
}