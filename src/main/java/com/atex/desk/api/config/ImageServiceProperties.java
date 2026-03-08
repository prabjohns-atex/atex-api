package com.atex.desk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the image processing service (Rust sidecar).
 */
@ConfigurationProperties(prefix = "desk.image-service")
public class ImageServiceProperties {

    /** Whether the image service is enabled */
    private boolean enabled = false;

    /** Base URL of the Rust image sidecar (e.g., "http://localhost:8090") */
    private String url = "http://localhost:8090";

    /** HMAC-SHA256 secret shared with the Rust service */
    private String secret = "changeme";

    /** Signature length (first N hex chars of HMAC) — Polopoly uses 7 */
    private int signatureLength = 7;

    /** Whether to redirect (302) or proxy image bytes through Java */
    private boolean redirect = true;

    /** Cache-Control max-age in seconds for image responses */
    private long cacheMaxAge = 86400;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public int getSignatureLength() { return signatureLength; }
    public void setSignatureLength(int signatureLength) { this.signatureLength = signatureLength; }

    public boolean isRedirect() { return redirect; }
    public void setRedirect(boolean redirect) { this.redirect = redirect; }

    public long getCacheMaxAge() { return cacheMaxAge; }
    public void setCacheMaxAge(long cacheMaxAge) { this.cacheMaxAge = cacheMaxAge; }
}
