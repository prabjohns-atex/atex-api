package com.atex.desk.api.controller;

import com.atex.desk.api.auth.CognitoAuthService;
import com.atex.desk.api.auth.DecodedToken;
import com.atex.desk.api.auth.LdapAuthService;
import com.atex.desk.api.auth.InvalidTokenException;
import com.atex.desk.api.auth.PasswordService;
import com.atex.desk.api.auth.TokenService;
import com.atex.desk.api.dto.CredentialsDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.TokenResponseDto;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/security")
@Tag(name = "Authentication")
public class SecurityController
{
    private static final String AUTH_HEADER = "X-Auth-Token";
    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(5);

    private final TokenService tokenService;
    private final AppUserRepository userRepository;
    private final PasswordService passwordService;
    private final LdapAuthService ldapAuthService;
    private final CognitoAuthService cognitoAuthService;

    public SecurityController(TokenService tokenService,
                              AppUserRepository userRepository,
                              PasswordService passwordService,
                              @Nullable LdapAuthService ldapAuthService,
                              @Nullable CognitoAuthService cognitoAuthService)
    {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.ldapAuthService = ldapAuthService;
        this.cognitoAuthService = cognitoAuthService;
    }

    /**
     * POST /security/token — Acquire an authentication token.
     * Accepts {username, password} and returns a JWT token.
     */
    @PostMapping("/token")
    @SecurityRequirements
    @Operation(summary = "Acquire authentication token",
               description = "Authenticate with username and password to receive a JWT token")
    @ApiResponse(responseCode = "200", description = "Authentication successful",
                 content = @Content(schema = @Schema(implementation = TokenResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "Authentication failed",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> acquireToken(@RequestBody CredentialsDto credentials)
    {
        if (credentials.getUsername() == null || credentials.getUsername().isBlank())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "No username present"));
        }
        if (credentials.getPassword() == null || credentials.getPassword().isBlank())
        {
            return ResponseEntity.badRequest()
                .body(new ErrorResponseDto("BAD_REQUEST", "No password present"));
        }

        AppUser user = userRepository.findByLoginName(credentials.getUsername()).orElse(null);
        if (user == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Authentication failed"));
        }

        if (!user.isActive())
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Account is disabled"));
        }

        // Dispatch to appropriate auth provider
        boolean authenticated = false;

        if (user.isLdap())
        {
            if (ldapAuthService == null)
            {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponseDto("SERVICE_UNAVAILABLE", "LDAP authentication not configured"));
            }
            authenticated = ldapAuthService.authenticate(credentials.getUsername(), credentials.getPassword());
        }
        else if (user.isRemote())
        {
            if (cognitoAuthService == null)
            {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponseDto("SERVICE_UNAVAILABLE", "Remote authentication not configured"));
            }
            authenticated = cognitoAuthService.authenticate(credentials.getUsername(), credentials.getPassword());
        }
        else
        {
            // Local password verification
            authenticated = passwordService.verify(credentials.getPassword(), user.getPasswordHash());
        }

        if (!authenticated)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Authentication failed"));
        }

        // Update login stats
        user.setLastLoginTime((int) (System.currentTimeMillis() / 1000));
        user.setNumLogins(user.getNumLogins() + 1);
        userRepository.save(user);

        // JWT sub = loginName
        String token = tokenService.createToken(
            user.getLoginName(),
            List.of("READ", "WRITE", "OWNER"),
            DEFAULT_EXPIRATION
        );

        DecodedToken decoded = decodeTokenSafe(token);

        TokenResponseDto response = new TokenResponseDto();
        response.setToken(token);
        response.setUserId(user.getLoginName());
        if (decoded != null && decoded.expiration() != null)
        {
            response.setExpireTime(Long.toString(decoded.expiration().getTime()));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * GET /security/token — Check if a token is still valid.
     */
    @GetMapping("/token")
    @Operation(summary = "Validate authentication token",
               description = "Check if the provided X-Auth-Token is still valid")
    @ApiResponse(responseCode = "200", description = "Token is valid",
                 content = @Content(schema = @Schema(implementation = TokenResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "Token is invalid or expired",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getToken(@RequestHeader(value = AUTH_HEADER, required = false) String authToken)
    {
        if (authToken == null || authToken.isBlank())
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Missing X-Auth-Token header"));
        }

        try
        {
            DecodedToken decoded = tokenService.decodeToken(authToken);
            TokenResponseDto response = new TokenResponseDto();
            response.setToken(authToken);
            response.setUserId(decoded.subject());
            if (decoded.expiration() != null)
            {
                response.setExpireTime(Long.toString(decoded.expiration().getTime()));
            }
            return ResponseEntity.ok(response);
        }
        catch (InvalidTokenException e)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Invalid token: " + e.getMessage()));
        }
    }

    /**
     * GET /security/oauth/url — Get the Cognito hosted UI login URL.
     */
    @GetMapping("/oauth/url")
    @SecurityRequirements
    @Operation(summary = "Get OAuth login URL",
               description = "Returns the Cognito hosted UI login URL for OAuth authentication")
    @ApiResponse(responseCode = "200", description = "OAuth URL returned")
    @ApiResponse(responseCode = "503", description = "Cognito not configured",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> getOAuthUrl(@RequestParam("callbackUrl") String callbackUrl)
    {
        if (cognitoAuthService == null)
        {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponseDto("SERVICE_UNAVAILABLE", "Cognito authentication not configured"));
        }

        String url = cognitoAuthService.getOAuthUrl(callbackUrl);
        if (url == null)
        {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponseDto("SERVICE_UNAVAILABLE", "Cognito domain not configured"));
        }

        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * POST /security/oauth/callback — Exchange OAuth code/token for a desk-api JWT.
     * Accepts either {code, callbackUrl} for authorization code flow,
     * or {url} for implicit flow (token in URL fragment).
     */
    @PostMapping("/oauth/callback")
    @SecurityRequirements
    @Operation(summary = "OAuth callback",
               description = "Exchange an OAuth authorization code or token for a desk-api JWT token")
    @ApiResponse(responseCode = "200", description = "Authentication successful",
                 content = @Content(schema = @Schema(implementation = TokenResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "Authentication failed",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    @ApiResponse(responseCode = "503", description = "Cognito not configured",
                 content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    public ResponseEntity<?> oauthCallback(@RequestBody Map<String, String> body)
    {
        if (cognitoAuthService == null)
        {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponseDto("SERVICE_UNAVAILABLE", "Cognito authentication not configured"));
        }

        CognitoAuthService.CognitoUser cognitoUser = null;

        // Try code flow first
        String code = body.get("code");
        String callbackUrl = body.get("callbackUrl");
        if (code != null && !code.isBlank() && callbackUrl != null && !callbackUrl.isBlank())
        {
            cognitoUser = cognitoAuthService.exchangeCodeForToken(code, callbackUrl);
        }
        else
        {
            // Try URL-based flow (implicit or code in URL)
            String url = body.get("url");
            if (url != null && !url.isBlank())
            {
                String resolvedCallbackUrl = callbackUrl != null ? callbackUrl
                    : (url.contains("?") ? url.substring(0, url.indexOf('?')) : url);
                cognitoUser = cognitoAuthService.verifyOAuthUrl(url, resolvedCallbackUrl);
            }
        }

        if (cognitoUser == null || cognitoUser.getUsername() == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "OAuth authentication failed"));
        }

        // Ensure local user exists
        String loginName = cognitoAuthService.ensureLocalUser(cognitoUser.getUsername());
        if (loginName == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED",
                    "No local user found and auto-creation is disabled"));
        }

        // Issue desk-api JWT
        String token = tokenService.createToken(
            loginName,
            List.of("READ", "WRITE", "OWNER"),
            DEFAULT_EXPIRATION
        );

        DecodedToken decoded = decodeTokenSafe(token);

        TokenResponseDto response = new TokenResponseDto();
        response.setToken(token);
        response.setUserId(loginName);
        if (decoded != null && decoded.expiration() != null)
        {
            response.setExpireTime(Long.toString(decoded.expiration().getTime()));
        }

        return ResponseEntity.ok(response);
    }

    private DecodedToken decodeTokenSafe(String token)
    {
        try
        {
            return tokenService.decodeToken(token);
        }
        catch (InvalidTokenException e)
        {
            return null;
        }
    }
}
