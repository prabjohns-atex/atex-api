package com.atex.integration;

/**
 * SSO utility for encoding login tokens in Solr proxy queries.
 * Stub implementation — SSO disabled by default.
 */
public class SSOUtil {

    public boolean isEnabled() { return false; }

    public String getSSOQueryName() { return "ssoToken"; }

    public String encode(String loginName, long ttl) { return ""; }

    public long getSSOTTL() { return 300000L; }
}
