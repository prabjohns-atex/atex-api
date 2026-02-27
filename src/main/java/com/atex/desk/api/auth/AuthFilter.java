package com.atex.desk.api.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet filter that validates JWT tokens from the X-Auth-Token header.
 * Ported from OneCMS AuthFilter.
 */
public class AuthFilter implements Filter
{
    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);
    public static final String AUTH_HEADER = "X-Auth-Token";
    public static final String USER_ATTRIBUTE = "desk.auth.user";
    public static final String TOKEN_ATTRIBUTE = "desk.auth.token";

    private final TokenService tokenService;
    private final boolean enabled;

    public AuthFilter(TokenService tokenService, boolean enabled)
    {
        this.tokenService = tokenService;
        this.enabled = enabled;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException
    {
        if (!enabled)
        {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String token = httpRequest.getHeader(AUTH_HEADER);
        if (token == null || token.isBlank())
        {
            httpResponse.setHeader("WWW-Authenticate", "X-Auth-Token");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Auth-Token header");
            return;
        }

        try
        {
            DecodedToken decoded = tokenService.decodeToken(token);
            request.setAttribute(USER_ATTRIBUTE, decoded.subject());
            request.setAttribute(TOKEN_ATTRIBUTE, decoded);
            chain.doFilter(request, response);
        }
        catch (InvalidTokenException e)
        {
            LOG.debug("Token validation failed: {}", e.getMessage());
            httpResponse.setHeader("WWW-Authenticate", "X-Auth-Token");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: " + e.getMessage());
        }
    }
}
