package com.atex.desk.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT token service ported from OneCMS TokenServiceImpl.
 * Uses RS256 (RSA + SHA-256) with JJWT 0.12.x.
 */
@Component
public class TokenService
{
    private static final Logger LOG = LoggerFactory.getLogger(TokenService.class);

    private static final String SCOPE = "scp";
    private static final String TARGET = "tgt";
    private static final String IMPERSONATING = "imp";

    private final TokenProperties properties;
    private final KeyPair keyPair;

    public TokenService(TokenProperties properties)
    {
        this.properties = properties;

        if (properties.getPrivateKey() != null && !properties.getPrivateKey().isBlank()
            && properties.getPublicKey() != null && !properties.getPublicKey().isBlank())
        {
            this.keyPair = decodeKeys(properties.getPrivateKey(), properties.getPublicKey());
            LOG.info("JWT authentication enabled with configured RSA keys (instanceId={})",
                properties.getInstanceId());
        }
        else
        {
            this.keyPair = generateKeyPair();
            String pub = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String pri = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            LOG.warn("No RSA keys configured — generated ephemeral key pair for development");
            LOG.info("desk.auth.public-key={}", pub);
            LOG.info("desk.auth.private-key={}", pri);

            // Generate a dev token for convenience
            String devToken = createToken("system", List.of("READ", "WRITE", "OWNER"),
                Duration.ofHours(24));
            LOG.info("Dev token (24h, user=system): {}", devToken);
        }
    }

    /**
     * Decode and validate a JWT token.
     *
     * @throws InvalidTokenException if the token is invalid, expired, or has wrong audience.
     */
    public DecodedToken decodeToken(String token) throws InvalidTokenException
    {
        try
        {
            Claims body = Jwts.parser()
                .requireAudience(properties.getInstanceId())
                .clockSkewSeconds(properties.getClockSkew())
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String subject = body.getSubject();
            if (subject == null || subject.isBlank())
            {
                throw new InvalidTokenException("empty subject");
            }

            String impersonating = body.get(IMPERSONATING, String.class);

            List<String> permissions = extractList(body.get(SCOPE, String.class));
            List<String> targets = extractList(body.get(TARGET, String.class));

            Date expiration = body.getExpiration();

            return new DecodedToken(subject, impersonating, permissions, targets, expiration);
        }
        catch (IncorrectClaimException e)
        {
            LOG.debug("Invalid token claim: {}", e.getMessage());
            throw new InvalidTokenException("invalid token claim");
        }
        catch (JwtException | IllegalArgumentException e)
        {
            LOG.debug("Invalid token: {}", e.getMessage());
            throw new InvalidTokenException("invalid token");
        }
    }

    /**
     * Validate a token and return the subject (user ID).
     */
    public String validate(String token) throws InvalidTokenException
    {
        return decodeToken(token).subject();
    }

    /**
     * Create a JWT token. Used for development/testing and by the token endpoint.
     */
    public String createToken(String subject, List<String> permissions, Duration expiration)
    {
        Instant now = Instant.now();

        Duration clampedExpiration = expiration;
        if (properties.getMaxLifetime() > 0)
        {
            clampedExpiration = Duration.ofSeconds(
                Math.min(expiration.getSeconds(), properties.getMaxLifetime())
            );
        }

        var builder = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(subject)
            .issuer("desk-api")
            .audience().add(properties.getInstanceId()).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(clampedExpiration)));

        if (permissions != null && !permissions.isEmpty())
        {
            builder.claim(SCOPE, String.join(",", permissions));
        }

        return builder
            .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
            .compact();
    }

    public KeyPair getKeyPair()
    {
        return keyPair;
    }

    // --- Internal helpers ---

    private List<String> extractList(String commaSeparated)
    {
        if (commaSeparated == null || commaSeparated.isBlank())
        {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static KeyPair decodeKeys(String privateKeyBase64, String publicKeyBase64)
    {
        try
        {
            byte[] priBytes = Base64.getDecoder().decode(privateKeyBase64);
            byte[] pubBytes = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(priBytes));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(pubBytes));
            return new KeyPair(publicKey, privateKey);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException e)
        {
            throw new RuntimeException("Failed to decode RSA keys", e);
        }
    }

    private static KeyPair generateKeyPair()
    {
        try
        {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException("RSA not available", e);
        }
    }
}
