package com.atex.onecms.app.dam.ws;

import com.atex.desk.api.auth.AuthFilter;
import com.atex.desk.api.auth.DecodedToken;
import com.atex.onecms.content.Subject;
import com.polopoly.user.server.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Helper to extract authenticated user context from Spring HTTP request.
 * Replaces the JAX-RS @AuthUser / UserContext pattern.
 */
public class DamUserContext {

    private final HttpServletRequest request;
    private final String userId;
    private final DecodedToken token;

    public DamUserContext(HttpServletRequest request) {
        this.request = request;
        this.userId = (String) request.getAttribute(AuthFilter.USER_ATTRIBUTE);
        this.token = (DecodedToken) request.getAttribute(AuthFilter.TOKEN_ATTRIBUTE);
    }

    public boolean isLoggedIn() {
        return userId != null && token != null;
    }

    public DamUserContext assertLoggedIn() {
        if (!isLoggedIn()) {
            throw new ContentApiException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return this;
    }

    public Subject getSubject() {
        assertLoggedIn();
        return new Subject(userId, null);
    }

    public Caller getCaller() {
        assertLoggedIn();
        return new Caller(userId);
    }

    public String getAuthToken() {
        return request.getHeader(AuthFilter.AUTH_HEADER);
    }

    /**
     * Check permission using JWT token scopes.
     * OWNER scope grants all permissions.
     * Permission strings may be prefixed with "21" (decoration) which is stripped before matching.
     */
    public boolean havePermission(com.polopoly.cm.ContentId contentId, String permission, boolean check) {
        if (!isLoggedIn() || token == null) {
            return false;
        }

        List<String> scopes = token.permissions();
        if (scopes == null || scopes.isEmpty()) {
            return false;
        }

        // OWNER scope grants all permissions
        if (scopes.contains("OWNER")) {
            return true;
        }

        if (permission == null || permission.isEmpty()) {
            return false;
        }

        // Strip "21" decoration prefix if present
        String perm = permission;
        if (perm.startsWith("21")) {
            perm = perm.substring(2);
        }

        // Map permission to required scope
        String upperPerm = perm.toUpperCase();
        if ("READ".equals(upperPerm)) {
            return scopes.contains("READ");
        } else if ("WRITE".equals(upperPerm) || "CREATE".equals(upperPerm)) {
            return scopes.contains("WRITE");
        }

        // Unknown permission — default to requiring WRITE scope
        return scopes.contains("WRITE");
    }

    public static DamUserContext from(HttpServletRequest request) {
        return new DamUserContext(request);
    }
}
