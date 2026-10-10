package edu.dbinsight.auth;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.AppUserRepository;
import edu.dbinsight.persistence.PasswordResetTokenEntity;
import edu.dbinsight.persistence.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration RESEND_DELAY = Duration.ofMinutes(1);

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String fromAddress;
    private final String frontendOrigin;
    private final Duration tokenLifetime;

    public PasswordResetService(AppUserRepository appUserRepository,
                                PasswordResetTokenRepository tokenRepository,
                                JavaMailSender mailSender,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.auth.password-reset.enabled:false}") boolean enabled,
                                @Value("${app.auth.password-reset.from:no-reply@localhost}") String fromAddress,
                                @Value("${app.auth.password-reset.frontend-origin:http://localhost:5173}") String frontendOrigin,
                                @Value("${app.auth.password-reset.token-ttl-minutes:30}") long tokenLifetimeMinutes) {
        this.appUserRepository = appUserRepository;
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.frontendOrigin = frontendOrigin;
        this.tokenLifetime = Duration.ofMinutes(tokenLifetimeMinutes);
    }

    @Transactional
    public void requestReset(String email) {
        requireEnabled();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        appUserRepository.findByEmail(normalizedEmail).ifPresent(user -> sendReset(user));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        requireEnabled();
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long.");
        }
        if (token == null || token.isBlank()) {
            throw invalidToken();
        }

        Instant now = Instant.now();
        PasswordResetTokenEntity resetToken = tokenRepository.findForUpdateByTokenHash(hashToken(token))
                .orElseThrow(PasswordResetService::invalidToken);
        if (resetToken.getConsumedAt() != null || !resetToken.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        AppUserEntity user = resetToken.getUser();
        user.updatePasswordHash(passwordEncoder.encode(newPassword));
        tokenRepository.findByUserUserIdAndConsumedAtIsNull(user.getUserId())
                .forEach(activeToken -> activeToken.consume(now));
    }

    private void sendReset(AppUserEntity user) {
        Instant now = Instant.now();
        boolean recentlySent = tokenRepository.findFirstByUserUserIdOrderByCreatedAtDesc(user.getUserId())
                .map(token -> token.getCreatedAt().isAfter(now.minus(RESEND_DELAY)))
                .orElse(false);
        if (recentlySent) {
            return;
        }

        tokenRepository.deleteByUserUserId(user.getUserId());
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokenRepository.save(new PasswordResetTokenEntity(user, hashToken(rawToken), now.plus(tokenLifetime), now));

        String resetUrl = frontendOrigin.replaceAll("/+$", "") + "/#resetToken=" + rawToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("Reset your DBInsight password");
        message.setText("A password reset was requested for your DBInsight account.\n\n"
                + "Use this one-time link within " + tokenLifetime.toMinutes() + " minutes:\n"
                + resetUrl + "\n\nIf you did not request a reset, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send the password reset email. Check the SMTP configuration and try again.",
                    exception);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset email is not configured on this server.");
        }
    }

    private static String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This password reset link is invalid or expired.");
    }
}
