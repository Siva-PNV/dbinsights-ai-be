package edu.dbinsight.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.AppUserRepository;
import edu.dbinsight.persistence.EmailVerificationTokenEntity;
import edu.dbinsight.persistence.EmailVerificationTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class EmailVerificationServiceTest {
    private static final String EMAIL = "user@example.com";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("#verifyToken=([A-Za-z0-9_-]+)");

    @Test
    void registrationCreatesAnAccountAndEmailsAHashedToken() throws Exception {
        AppUserRepository users = mock(AppUserRepository.class);
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.save(org.mockito.ArgumentMatchers.any(AppUserEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppUserEntity created = service(users, tokens, encoder, mailSender, true)
                .register("Test User", EMAIL, "StrongPass123");

        assertEquals(EMAIL, created.getEmail());
        org.mockito.ArgumentCaptor<EmailVerificationTokenEntity> tokenCaptor =
                org.mockito.ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        org.mockito.ArgumentCaptor<SimpleMailMessage> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(tokens).save(tokenCaptor.capture());
        verify(mailSender).send(messageCaptor.capture());
        Matcher matcher = TOKEN_PATTERN.matcher(messageCaptor.getValue().getText());
        assertTrue(matcher.find());
        String hash = (String) ReflectionTestUtils.getField(tokenCaptor.getValue(), "tokenHash");
        assertEquals(hashToken(matcher.group(1)), hash);
        assertNotEquals(matcher.group(1), hash);
        assertTrue(encoder.matches("StrongPass123", created.getPasswordHash()));
    }

    @Test
    void validTokenMarksTheAddressVerified() {
        String rawToken = "verification-token";
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity(
                user(), hashToken(rawToken), Instant.now().plusSeconds(3600), Instant.now());
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        when(tokens.findForUpdateByTokenHash(hashToken(rawToken))).thenReturn(Optional.of(token));

        service(mock(AppUserRepository.class), tokens, new BCryptPasswordEncoder(),
                mock(JavaMailSender.class), true).verify(rawToken);

        assertTrue(token.getVerifiedAt() != null);
    }

    @Test
    void expiredTokenIsRejected() {
        String rawToken = "expired-verification-token";
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity(
                user(), hashToken(rawToken), Instant.now().minusSeconds(1), Instant.now().minusSeconds(3600));
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        when(tokens.findForUpdateByTokenHash(hashToken(rawToken))).thenReturn(Optional.of(token));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(mock(AppUserRepository.class), tokens, new BCryptPasswordEncoder(),
                        mock(JavaMailSender.class), true).verify(rawToken));

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void existingAccountsWithoutVerificationRowsRemainVerified() {
        AppUserEntity user = user();
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        when(tokens.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());

        assertTrue(service(mock(AppUserRepository.class), tokens, new BCryptPasswordEncoder(),
                mock(JavaMailSender.class), true).isVerified(user));
    }

    private static EmailVerificationService service(AppUserRepository users,
                                                    EmailVerificationTokenRepository tokens,
                                                    PasswordEncoder encoder,
                                                    JavaMailSender mailSender,
                                                    boolean enabled) {
        return new EmailVerificationService(users, tokens, encoder, mailSender, enabled,
                "no-reply@example.com", "http://localhost:5173", 24);
    }

    private static AppUserEntity user() {
        AppUserEntity user = new AppUserEntity("Test User", EMAIL, "old-hash", "USER", Instant.now());
        ReflectionTestUtils.setField(user, "userId", 17L);
        return user;
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
