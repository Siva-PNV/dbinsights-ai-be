package edu.dbinsight.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.AppUserRepository;
import edu.dbinsight.persistence.PasswordResetTokenEntity;
import edu.dbinsight.persistence.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PasswordResetServiceTest {
    private static final String EMAIL = "user@example.com";
    private static final Pattern RESET_TOKEN_PATTERN = Pattern.compile("#resetToken=([A-Za-z0-9_-]+)");

    @Test
    void unknownEmailDoesNotSendMailOrCreateToken() {
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service(users, tokens, mailSender, true).requestReset(EMAIL);

        verifyNoInteractions(tokens, mailSender);
    }

    @Test
    void resetEmailContainsRawTokenWhileDatabaseStoresOnlyItsHash() throws Exception {
        AppUserEntity user = user();
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokens.findFirstByUserUserIdOrderByCreatedAtDesc(user.getUserId())).thenReturn(Optional.empty());

        service(users, tokens, mailSender, true).requestReset(EMAIL);

        org.mockito.ArgumentCaptor<PasswordResetTokenEntity> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        org.mockito.ArgumentCaptor<SimpleMailMessage> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(tokens).save(tokenCaptor.capture());
        verify(mailSender).send(messageCaptor.capture());

        Matcher matcher = RESET_TOKEN_PATTERN.matcher(messageCaptor.getValue().getText());
        assertTrue(matcher.find());
        String rawToken = matcher.group(1);
        String storedHash = (String) ReflectionTestUtils.getField(tokenCaptor.getValue(), "tokenHash");
        assertEquals(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))), storedHash);
        assertFalse(storedHash.equals(rawToken));
    }

    @Test
    void validTokenUpdatesPasswordAndConsumesOutstandingTokens() {
        AppUserEntity user = user();
        String rawToken = "valid-reset-token";
        PasswordResetTokenEntity resetToken = new PasswordResetTokenEntity(
                user, tokenHash(rawToken), Instant.now().plusSeconds(600), Instant.now());
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        when(tokens.findForUpdateByTokenHash(tokenHash(rawToken))).thenReturn(Optional.of(resetToken));
        when(tokens.findByUserUserIdAndConsumedAtIsNull(user.getUserId())).thenReturn(List.of(resetToken));
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        service(users, tokens, mock(JavaMailSender.class), true).resetPassword(rawToken, "newSecurePass123");

        assertTrue(encoder.matches("newSecurePass123", user.getPasswordHash()));
        assertTrue(resetToken.getConsumedAt() != null);
    }

    @Test
    void expiredTokenIsRejected() {
        String rawToken = "expired-reset-token";
        PasswordResetTokenEntity expiredToken = new PasswordResetTokenEntity(
                user(), tokenHash(rawToken), Instant.now().minusSeconds(1), Instant.now().minusSeconds(600));
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        when(tokens.findForUpdateByTokenHash(tokenHash(rawToken))).thenReturn(Optional.of(expiredToken));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(mock(AppUserRepository.class), tokens, mock(JavaMailSender.class), true)
                        .resetPassword(rawToken, "newSecurePass123"));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void resetIsUnavailableUntilEmailIsConfigured() {
        PasswordResetService service = service(
                mock(AppUserRepository.class),
                mock(PasswordResetTokenRepository.class),
                mock(JavaMailSender.class),
                false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.requestReset(EMAIL));

        assertEquals(503, exception.getStatusCode().value());
    }

    private static PasswordResetService service(AppUserRepository users,
                                                PasswordResetTokenRepository tokens,
                                                JavaMailSender mailSender,
                                                boolean enabled) {
        return new PasswordResetService(users, tokens, mailSender, new BCryptPasswordEncoder(),
                enabled, "no-reply@example.com", "http://localhost:5173", 30);
    }

    private static AppUserEntity user() {
        AppUserEntity user = new AppUserEntity("Test User", EMAIL, "old-hash", "USER", Instant.now());
        ReflectionTestUtils.setField(user, "userId", 17L);
        return user;
    }

    private static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
