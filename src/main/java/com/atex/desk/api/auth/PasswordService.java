package com.atex.desk.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Multi-scheme password verification supporting all Polopoly password formats.
 */
@Component
public class PasswordService
{
    private static final Logger LOG = LoggerFactory.getLogger(PasswordService.class);

    // Polopoly static secret for OLDSHA hashing
    private static final byte[] OLDSHA_SECRET = {
        -123, -108, -35, -10, 123, -16, -52, 73, -56, 86, -40, 22, -20, -80, 102, 50
    };

    /**
     * Verify a password against a stored hash, auto-detecting the scheme.
     */
    public boolean verify(String password, String storedHash)
    {
        if (password == null || storedHash == null)
        {
            return false;
        }

        String scheme = detectScheme(storedHash);
        return switch (scheme)
        {
            case "OLDSHA" -> verifyOldSha(password, storedHash);
            case "SHA256" -> verifySha256(password, storedHash);
            case "SHA" -> verifyPrefixed(password, storedHash, "{SHA}", "SHA-1", 0);
            case "SSHA" -> verifyPrefixed(password, storedHash, "{SSHA}", "SHA-1", 20);
            case "MD5" -> verifyPrefixed(password, storedHash, "{MD5}", "MD5", 0);
            case "SMD5" -> verifyPrefixed(password, storedHash, "{SMD5}", "MD5", 16);
            case "CLEARTEXT" -> password.equals(storedHash.substring("{CLEARTEXT}".length()));
            case "LDAPUSER", "REMOTEUSER", "COGNITOUSER" -> false;
            default -> false;
        };
    }

    /**
     * Detect the password scheme from the stored hash format.
     */
    public String detectScheme(String storedHash)
    {
        if (storedHash == null || storedHash.isEmpty())
        {
            return "UNKNOWN";
        }

        if (storedHash.startsWith("{SHA}")) return "SHA";
        if (storedHash.startsWith("{SSHA}")) return "SSHA";
        if (storedHash.startsWith("{MD5}")) return "MD5";
        if (storedHash.startsWith("{SMD5}")) return "SMD5";
        if (storedHash.startsWith("{CLEARTEXT}")) return "CLEARTEXT";
        if (storedHash.startsWith("{LDAPUSER}")) return "LDAPUSER";
        if (storedHash.startsWith("{REMOTEUSER}")) return "REMOTEUSER";
        if (storedHash.startsWith("{COGNITOUSER}")) return "COGNITOUSER";

        // No prefix — determine by length
        if (storedHash.length() == 16 && storedHash.matches("[0-9a-f]+"))
        {
            return "OLDSHA";
        }
        if (storedHash.length() == 64 && storedHash.matches("[0-9a-f]+"))
        {
            return "SHA256";
        }

        return "UNKNOWN";
    }

    /**
     * Hash a password using the Polopoly OLDSHA scheme.
     * SHA-1(staticSecret || password), first 8 bytes as hex.
     */
    public String hashOldSha(String password)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(OLDSHA_SECRET);
            md.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++)
            {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Hash a password using SHA-256 (for backward compatibility).
     */
    public String hashSha256(String password)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
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

    private boolean verifyOldSha(String password, String storedHash)
    {
        return storedHash.equals(hashOldSha(password));
    }

    private boolean verifySha256(String password, String storedHash)
    {
        return storedHash.equals(hashSha256(password));
    }

    /**
     * Verify prefixed password formats ({SHA}, {SSHA}, {MD5}, {SMD5}).
     * @param saltLength 0 for unsalted, >0 for salted (salt appended after digest)
     */
    private boolean verifyPrefixed(String password, String storedHash,
                                   String prefix, String algorithm, int saltLength)
    {
        try
        {
            String encoded = storedHash.substring(prefix.length());
            byte[] decoded = Base64.getDecoder().decode(encoded);

            MessageDigest md = MessageDigest.getInstance(algorithm);

            if (saltLength > 0)
            {
                // Salted: decoded = digest + salt
                int digestLength = decoded.length - saltLength;
                if (digestLength <= 0)
                {
                    // Fallback: some implementations use variable salt sizes
                    digestLength = md.getDigestLength();
                }
                byte[] salt = new byte[decoded.length - digestLength];
                System.arraycopy(decoded, digestLength, salt, 0, salt.length);

                md.update(password.getBytes(StandardCharsets.UTF_8));
                md.update(salt);
                byte[] computed = md.digest();

                return constantTimeEquals(decoded, 0, digestLength, computed, 0, computed.length);
            }
            else
            {
                // Unsalted
                byte[] computed = md.digest(password.getBytes(StandardCharsets.UTF_8));
                return constantTimeEquals(decoded, 0, decoded.length, computed, 0, computed.length);
            }
        }
        catch (Exception e)
        {
            LOG.warn("Error verifying {} password: {}", prefix, e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(byte[] a, int aOff, int aLen,
                                       byte[] b, int bOff, int bLen)
    {
        if (aLen != bLen) return false;
        int result = 0;
        for (int i = 0; i < aLen; i++)
        {
            result |= a[aOff + i] ^ b[bOff + i];
        }
        return result == 0;
    }
}
