package edu.dbinsight.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.dbinsight.persistence.AppUserEntity;
import edu.dbinsight.persistence.AppUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerSessionTest {
    @Test
    void registrationWithVerificationEnabledDoesNotCreateASession() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AppUserEntity user = new AppUserEntity("Test User", "user@example.com", "hash", "USER", Instant.now());
        when(emailVerificationService.register("Test User", "user@example.com", "StrongPass123")).thenReturn(user);
        when(emailVerificationService.isEnabled()).thenReturn(true);
        AuthController controller = new AuthController(
                authenticationManager,
                appUserRepository,
                new HttpSessionSecurityContextRepository(),
                mock(PasswordResetService.class),
                emailVerificationService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        AuthController.RegistrationResponse result = controller.register(
                new AuthController.RegisterRequest("Test User", "user@example.com", "StrongPass123"),
                request, new MockHttpServletResponse());

        assertTrue(result.verificationRequired());
        assertNull(result.user());
        assertNull(request.getSession(false));
        verify(authenticationManager, never()).authenticate(any(Authentication.class));
    }

    @Test
    void registrationWithoutVerificationRequiresManualSignIn() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        when(emailVerificationService.register("Test User", "user@example.com", "StrongPass123"))
                .thenReturn(new AppUserEntity("Test User", "user@example.com", "hash", "USER", Instant.now()));
        when(emailVerificationService.isEnabled()).thenReturn(false);
        AuthController controller = new AuthController(
                authenticationManager,
                appUserRepository,
                new HttpSessionSecurityContextRepository(),
                mock(PasswordResetService.class),
                emailVerificationService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        AuthController.RegistrationResponse result = controller.register(
                new AuthController.RegisterRequest("Test User", "user@example.com", "StrongPass123"),
                request, new MockHttpServletResponse());

        assertEquals("Account created. Please sign in.", result.message());
        assertNull(result.user());
        assertNull(request.getSession(false));
        verify(authenticationManager, never()).authenticate(any(Authentication.class));
    }

    @Test
    void passwordResetInvalidatesTheCurrentSession() {
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        AuthController controller = new AuthController(
                mock(AuthenticationManager.class),
                mock(AppUserRepository.class),
                new HttpSessionSecurityContextRepository(),
                passwordResetService,
                mock(EmailVerificationService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        controller.resetPassword(new AuthController.PasswordChangeRequest("token", "new-password"), request);

        verify(passwordResetService).resetPassword("token", "new-password");
        assertNull(request.getSession(false));
    }

    @Test
    void loginPersistsAuthenticationInHttpSession() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(new AppUserEntity(
                "Test User", "user@example.com", "hash", "USER", Instant.now())));
        when(emailVerificationService.isVerified(any(AppUserEntity.class))).thenReturn(true);

        AuthController controller = new AuthController(
                authenticationManager,
                appUserRepository,
                new HttpSessionSecurityContextRepository(),
                mock(PasswordResetService.class),
                emailVerificationService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.login(new AuthController.LoginRequest("user@example.com", "StrongPass123"),
                request, new MockHttpServletResponse());

        SecurityContext savedContext = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(savedContext);
        assertEquals("user@example.com", savedContext.getAuthentication().getName());
    }

    @Test
    void invalidCredentialsReturnUnauthorizedWithoutCreatingASession() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        AuthController controller = new AuthController(
                authenticationManager,
                appUserRepository,
                new HttpSessionSecurityContextRepository(),
                mock(PasswordResetService.class),
                mock(EmailVerificationService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.login(new AuthController.LoginRequest("user@example.com", "wrong-password"),
                        request, new MockHttpServletResponse()));

        assertEquals(401, exception.getStatusCode().value());
        verifyNoInteractions(appUserRepository);
        assertNull(request.getSession(false));
    }

    @Test
    void unverifiedUserCannotCreateSession() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(new AppUserEntity(
                "Test User", "user@example.com", "hash", "USER", Instant.now())));
        when(emailVerificationService.isVerified(any(AppUserEntity.class))).thenReturn(false);
        AuthController controller = new AuthController(
                authenticationManager,
                appUserRepository,
                new HttpSessionSecurityContextRepository(),
                mock(PasswordResetService.class),
                emailVerificationService);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.login(new AuthController.LoginRequest("user@example.com", "StrongPass123"),
                        request, new MockHttpServletResponse()));

        assertEquals(403, exception.getStatusCode().value());
        assertNull(request.getSession(false));
    }
}
