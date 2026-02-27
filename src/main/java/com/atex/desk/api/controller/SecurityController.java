package com.atex.desk.api.controller;

import com.atex.desk.api.auth.DecodedToken;
import com.atex.desk.api.auth.InvalidTokenException;
import com.atex.desk.api.auth.TokenService;
import com.atex.desk.api.dto.CredentialsDto;
import com.atex.desk.api.dto.ErrorResponseDto;
import com.atex.desk.api.dto.TokenResponseDto;
import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/security")
public class SecurityController
{
    private static final String AUTH_HEADER = "X-Auth-Token";
    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(5);

    private final TokenService tokenService;
    private final AppUserRepository userRepository;

    public SecurityController(TokenService tokenService, AppUserRepository userRepository)
    {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    /**
     * POST /security/token — Acquire an authentication token.
     * Accepts {username, password} and returns a JWT token.
     */
    @PostMapping("/token")
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

        AppUser user = userRepository.findByUsername(credentials.getUsername()).orElse(null);
        if (user == null)
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Authentication failed"));
        }

        String hash = sha256(credentials.getPassword());
        if (!hash.equals(user.getPasswordHash()))
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponseDto("UNAUTHORIZED", "Authentication failed"));
        }

        String token = tokenService.createToken(
            user.getUserId().toString(),
            List.of("READ", "WRITE", "OWNER"),
            DEFAULT_EXPIRATION
        );

        DecodedToken decoded = decodeTokenSafe(token);

        TokenResponseDto response = new TokenResponseDto();
        response.setToken(token);
        response.setUserId(user.getUserId().toString());
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

    private static String sha256(String input)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}
