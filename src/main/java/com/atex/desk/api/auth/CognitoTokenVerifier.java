package com.atex.desk.api.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cognito JWT token verifier — consolidates Polopoly's JWK infrastructure
 * (Jwk, JwkProvider, UrlJwkProvider, UrlSigningKeyResolver, TokenDecoder)
 * into a single class.
 * <p>
 * Implements JJWT 0.12.x {@link Locator} interface to resolve signing keys
 * by {@code kid} from Cognito's JWKS endpoint.
 */
public class CognitoTokenVerifier implements Locator<Key>
{
    private static final Logger LOG = LoggerFactory.getLogger(CognitoTokenVerifier.class);

    private static final ConcurrentHashMap<String, Key> KEY_CACHE = new ConcurrentHashMap<>();

    private final String jwksUrl;
    private final String issuerUrl;
    private final CognitoProperties properties;

    public CognitoTokenVerifier(CognitoProperties properties)
    {
        this.properties = properties;
        this.jwksUrl = properties.getJwksUrl();
        this.issuerUrl = properties.getIssuerUrl();
    }

    /**
     * Verify a Cognito JWT token: validate signature, issuer, and extract user claims.
     *
     * @param token the raw JWT string
     * @return CognitoUser with username, email, and groups extracted from claims
     * @throws InvalidTokenException if the token is invalid
     */
    public CognitoAuthService.CognitoUser verify(String token) throws InvalidTokenException
    {
        try
        {
            var claims = Jwts.parser()
                .keyLocator(this)
                .requireIssuer(issuerUrl)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            CognitoAuthService.CognitoUser user = new CognitoAuthService.CognitoUser();
            user.setIdToken(token);

            // Username: try cognito:username first, then preferred_username, then sub
            String username = claims.get("cognito:username", String.class);
            if (username == null)
            {
                username = claims.get("preferred_username", String.class);
            }
            if (username == null)
            {
                username = claims.getSubject();
            }
            user.setUsername(username);

            // Email
            String email = claims.get("email", String.class);
            user.setEmail(email);

            // Groups
            @SuppressWarnings("unchecked")
            List<String> groups = claims.get("cognito:groups", List.class);
            if (groups != null)
            {
                user.setGroups(new ArrayList<>(groups));
            }

            return user;
        }
        catch (Exception e)
        {
            LOG.debug("Cognito token verification failed: {}", e.getMessage());
            throw new InvalidTokenException("Cognito token verification failed: " + e.getMessage());
        }
    }

    // ======== Locator<Key> implementation ========

    @Override
    public Key locate(Header header)
    {
        if (!(header instanceof ProtectedHeader protectedHeader))
        {
            throw new IllegalArgumentException("JWT header is not a signed/encrypted header");
        }

        String kid = protectedHeader.getKeyId();
        if (kid == null)
        {
            throw new IllegalArgumentException("JWT header has no kid");
        }

        return KEY_CACHE.computeIfAbsent(kid, this::fetchKey);
    }

    // ======== JWK fetching and parsing ========

    private Key fetchKey(String kid)
    {
        try
        {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksUrl))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                throw new RuntimeException("Failed to fetch JWKS: HTTP " + response.statusCode());
            }

            JsonObject jwks = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray keys = jwks.getAsJsonArray("keys");

            for (JsonElement elem : keys)
            {
                JsonObject keyObj = elem.getAsJsonObject();
                String keyKid = getStringOrNull(keyObj, "kid");
                if (kid.equals(keyKid))
                {
                    return parseJwk(keyObj);
                }
            }

            throw new RuntimeException("No JWK found for kid: " + kid);
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error fetching JWKS from " + jwksUrl, e);
        }
    }

    private PublicKey parseJwk(JsonObject jwk)
    {
        String kty = getStringOrNull(jwk, "kty");

        if ("RSA".equals(kty))
        {
            return parseRsaKey(jwk);
        }
        else if ("EC".equals(kty))
        {
            return parseEcKey(jwk);
        }
        else
        {
            throw new IllegalArgumentException("Unsupported key type: " + kty);
        }
    }

    private PublicKey parseRsaKey(JsonObject jwk)
    {
        try
        {
            byte[] nBytes = Base64.getUrlDecoder().decode(jwk.get("n").getAsString());
            byte[] eBytes = Base64.getUrlDecoder().decode(jwk.get("e").getAsString());

            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to parse RSA JWK", e);
        }
    }

    private PublicKey parseEcKey(JsonObject jwk)
    {
        try
        {
            String crv = getStringOrNull(jwk, "crv");
            byte[] xBytes = Base64.getUrlDecoder().decode(jwk.get("x").getAsString());
            byte[] yBytes = Base64.getUrlDecoder().decode(jwk.get("y").getAsString());

            BigInteger x = new BigInteger(1, xBytes);
            BigInteger y = new BigInteger(1, yBytes);
            ECPoint point = new ECPoint(x, y);

            String stdName = switch (crv)
            {
                case "P-256" -> "secp256r1";
                case "P-384" -> "secp384r1";
                case "P-521" -> "secp521r1";
                default -> throw new IllegalArgumentException("Unsupported EC curve: " + crv);
            };

            java.security.AlgorithmParameters parameters =
                java.security.AlgorithmParameters.getInstance("EC");
            parameters.init(new java.security.spec.ECGenParameterSpec(stdName));
            ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);

            ECPublicKeySpec spec = new ECPublicKeySpec(point, ecSpec);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePublic(spec);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to parse EC JWK", e);
        }
    }

    private static String getStringOrNull(JsonObject obj, String key)
    {
        JsonElement elem = obj.get(key);
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : null;
    }
}
