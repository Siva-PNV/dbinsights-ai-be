package edu.dbinsight.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AdminRequestGuardTest {
    private final AdminRequestGuard guard = new AdminRequestGuard("test-admin-token");

    @Test
    void onlyAdminRoleCanRemoveMonitoredDatabases() {
        var admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var user = new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertDoesNotThrow(() -> guard.requireAdminRole(admin));
        assertThrows(ResponseStatusException.class, () -> guard.requireAdminRole(user));
        assertThrows(ResponseStatusException.class, () -> guard.requireAdminRole(null));
    }
}