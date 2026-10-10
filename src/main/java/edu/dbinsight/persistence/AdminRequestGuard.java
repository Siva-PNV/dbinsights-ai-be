package edu.dbinsight.persistence;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminRequestGuard {
    private final String adminToken;

    public AdminRequestGuard(@Value("${app.security.admin-token:dbinsight-admin-token}") String adminToken) {
        this.adminToken = adminToken == null ? "dbinsight-admin-token" : adminToken;
    }

    public void requireAdminToken(String token) {
        if (Objects.equals(token, adminToken)) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                            || "ADMIN".equals(authority.getAuthority()));
            if (isAdmin) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "A valid X-DBInsight-Token header or authenticated ADMIN role is required for administrative actions.");
    }

    public void requireAdminRole(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an ADMIN user can remove monitored databases.");
    }
}
