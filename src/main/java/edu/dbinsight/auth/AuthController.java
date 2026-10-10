package edu.dbinsight.auth;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "${app.frontend-origin:http://localhost:5173}")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final SecurityContextRepository securityContextRepository;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthenticationManager authenticationManager,
                          AppUserRepository appUserRepository,
                          SecurityContextRepository securityContextRepository,
                          PasswordResetService passwordResetService,
                          EmailVerificationService emailVerificationService) {
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
        this.securityContextRepository = securityContextRepository;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public RegistrationResponse register(@RequestBody RegisterRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        String name = normalizeName(request.name());
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        if (appUserRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        AppUserEntity user = emailVerificationService.register(name, email, password);
        if (emailVerificationService.isEnabled()) {
            return new RegistrationResponse(
                    "Account created. Check your email for the verification link before signing in.",
                    true,
                    null);
        }

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password));
        establishSession(authentication, httpRequest, httpResponse);

        return new RegistrationResponse("Account created and signed in.", false,
                new AuthResponse(user.getUserId(), user.getName(), user.getEmail(), user.getRole(),
                        "Account created and signed in."));
    }

    @PostMapping("/login")
        public AuthResponse login(@RequestBody LoginRequest request,
                      HttpServletRequest httpRequest,
                      HttpServletResponse httpResponse) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        AppUserEntity user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
        if (!emailVerificationService.isVerified(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Verify your email address before signing in.");
        }
        establishSession(authentication, httpRequest, httpResponse);

        return new AuthResponse(user.getUserId(), user.getName(), user.getEmail(), user.getRole(),
                "Authentication successful.");
    }

    @GetMapping("/me")
    public AuthResponse currentUser(Authentication authentication) {
        AppUserEntity user = appUserRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        return new AuthResponse(user.getUserId(), user.getName(), user.getEmail(), user.getRole(),
                "Authenticated session restored.");
    }

    @PostMapping("/password/forgot")
    public MessageResponse requestPasswordReset(@RequestBody PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        passwordResetService.requestReset(email);
        return new MessageResponse("If an account exists for that email, a password reset link has been sent.");
    }

    @PostMapping("/email/verify")
    public MessageResponse verifyEmail(@RequestBody EmailVerificationRequest request) {
        emailVerificationService.verify(request.token());
        return new MessageResponse("Email verified. You can now sign in.");
    }

    @PostMapping("/email/verification/resend")
    public MessageResponse resendVerification(@RequestBody PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        emailVerificationService.resend(email);
        return new MessageResponse("If that account needs verification, a verification email has been sent.");
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@RequestBody PasswordChangeRequest request,
                                         HttpServletRequest httpRequest) {
        passwordResetService.resetPassword(request.token(), request.password());
        SecurityContextHolder.clearContext();
        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
        return new MessageResponse("Password updated. You can now sign in with your new password.");
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }

    private void establishSession(Authentication authentication,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        }
        return name.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email format is invalid.");
        }
        return normalized;
    }

    private String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long.");
        }
        return password;
    }

    public record RegisterRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record PasswordResetRequest(String email) {}
    public record EmailVerificationRequest(String token) {}
    public record PasswordChangeRequest(String token, String password) {}
    public record MessageResponse(String message) {}
    public record RegistrationResponse(String message, boolean verificationRequired, AuthResponse user) {}
    public record AuthResponse(Long userId, String name, String email, String role, String message) {}
}
