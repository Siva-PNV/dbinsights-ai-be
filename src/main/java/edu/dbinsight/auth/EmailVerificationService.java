package edu.dbinsight.auth;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.AppUserRepository;
import edu.dbinsight.persistence.EmailVerificationTokenEntity;
import edu.dbinsight.persistence.EmailVerificationTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration RESEND_DELAY = Duration.ofMinutes(1);

    private final AppUserRepository appUserRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final boolean enabled;
    private final String fromAddress;
    private final String frontendOrigin;
    private final Duration tokenLifetime;

    public EmailVerificationService(AppUserRepository appUserRepository,
                                    EmailVerificationTokenRepository tokenRepository,
                                    PasswordEncoder passwordEncoder,
    EmailSender emailSender,
                                    @Value("${app.auth.email-verification.enabled:false}") boolean enabled,
                                    @Value("${app.auth.password-reset.from:no-reply@localhost}") String fromAddress,
                                    @Value("${app.auth.password-reset.frontend-origin:http://localhost:5173}") String frontendOrigin,
                                    @Value("${app.auth.email-verification.token-ttl-hours:24}") long tokenLifetimeHours) {
        this.appUserRepository = appUserRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.frontendOrigin = frontendOrigin;
        this.tokenLifetime = Duration.ofHours(tokenLifetimeHours);
    }

    @Transactional
    public AppUserEntity register(String name, String email, String password) {
        AppUserEntity user = appUserRepository.save(new AppUserEntity(
                name, email.trim().toLowerCase(Locale.ROOT), passwordEncoder.encode(password), "USER", Instant.now()));
        if (enabled) {
            sendVerification(user);
        }
        return user;
    }

    @Transactional
    public void verify(String rawToken) {
        requireEnabled();
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        Instant now = Instant.now();
        EmailVerificationTokenEntity token = tokenRepository.findForUpdateByTokenHash(hashToken(rawToken))
                .orElseThrow(EmailVerificationService::invalidToken);
        if (token.getVerifiedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        token.markVerified(now);
    }

    @Transactional(readOnly = true)
    public boolean isVerified(AppUserEntity user) {
        return !enabled || tokenRepository.findByUserUserId(user.getUserId())
                .map(token -> token.getVerifiedAt() != null)
                .orElse(true);
    }

    @Transactional
    public void resend(String email) {
        requireEnabled();
        appUserRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(user ->
                tokenRepository.findByUserUserId(user.getUserId()).ifPresent(token -> {
                    if (token.getVerifiedAt() == null && token.getCreatedAt().isBefore(Instant.now().minus(RESEND_DELAY))) {
                        tokenRepository.delete(token);
                        tokenRepository.flush();
                        sendVerification(user);
                    }
                }));
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void sendVerification(AppUserEntity user) {
        Instant now = Instant.now();
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokenRepository.save(new EmailVerificationTokenEntity(
                user, hashToken(rawToken), now.plus(tokenLifetime), now));

        String verificationUrl = frontendOrigin.replaceAll("/+$", "") + "/#verifyToken=" + rawToken;
        String text = "Welcome to DBInsight.\n\n"
                + "Verify your email address using this one-time link within "
                + tokenLifetime.toHours() + " hours:\n"
                + verificationUrl + "\n\nIf you did not create this account, you can ignore this email.";
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            LOGGER.error("Failed to send email verification message.", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send the verification email. Check the email provider configuration and try again.",
                    exception);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email verification is not configured on this server.");
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
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link is invalid or expired.");
    }
}
