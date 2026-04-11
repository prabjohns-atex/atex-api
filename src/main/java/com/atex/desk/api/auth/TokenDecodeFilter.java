package com.atex.desk.api.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Non-rejecting token decoder filter.
 * <p>
 * Decodes the X-Auth-Token header if present and populates request attributes,
 * but does not reject requests without a token. Handlers that require
 * authentication call {@code DamUserContext.assertLoggedIn()} explicitly —
 * matching the Polopoly/Jersey per-endpoint authentication model.
 * <p>
 * This filter runs on ALL URL patterns (including plugin endpoints).
 * The legacy {@link AuthFilter} still runs on its configured patterns
 * for backward compatibility and fail-safe rejection.
 */
public class TokenDecodeFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(TokenDecodeFilter.class);

    private final TokenService tokenService;
    private final boolean enabled;

    public TokenDecodeFilter(TokenService tokenService, boolean enabled) {
        this.tokenService = tokenService;
        this.enabled = enabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String token = httpRequest.getHeader(AuthFilter.AUTH_HEADER);

        if (token != null && !token.isBlank()
                && httpRequest.getAttribute(AuthFilter.USER_ATTRIBUTE) == null) {
            try {
                DecodedToken decoded = tokenService.decodeToken(token);
                request.setAttribute(AuthFilter.USER_ATTRIBUTE, decoded.subject());
                request.setAttribute(AuthFilter.TOKEN_ATTRIBUTE, decoded);
            } catch (InvalidTokenException e) {
                LOG.debug("Token decode failed (non-fatal): {}", e.getMessage());
                // Intentionally fall through — handler will call assertLoggedIn() if needed
            }
        }

        chain.doFilter(request, response);
    }
}
