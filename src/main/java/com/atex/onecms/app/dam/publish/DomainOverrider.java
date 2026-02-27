package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DomainOverrider {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(https?://[^/]+)(/.*)?");

    private final RemoteConfigBean remoteConfig;

    public DomainOverrider(RemoteConfigBean remoteConfig) {
        this.remoteConfig = remoteConfig;
    }

    public String fixPublicationUrl(String url) {
        if (remoteConfig == null) return url;
        return fixUrl(url, remoteConfig.getDomainOverride());
    }

    public String fixJsPublicationUrl(String url) {
        if (remoteConfig == null) return url;
        return fixUrl(url, remoteConfig.getJsDomainOverride());
    }

    private String fixUrl(String url, String domainOverride) {
        if (url == null || url.isEmpty() || domainOverride == null || domainOverride.isEmpty()) {
            return url;
        }
        Matcher matcher = DOMAIN_PATTERN.matcher(url);
        if (matcher.matches()) {
            String path = matcher.group(2);
            // Ensure override doesn't end with / and path starts with /
            String override = domainOverride.endsWith("/")
                ? domainOverride.substring(0, domainOverride.length() - 1)
                : domainOverride;
            if (!override.startsWith("http")) {
                override = "https://" + override;
            }
            return override + (path != null ? path : "");
        }
        return url;
    }
}
