package com.atex.desk.integration.publish;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishing pipeline configuration.
 * Ported from gong/desk DamPublisherConfiguration.
 */
@Component
@ConfigurationProperties(prefix = "desk.integration.publishing")
public class PublishingConfig {

    private boolean enabled = false;

    /** Remote CMS base URL (e.g., https://target-cms.example.com/onecms) */
    private String remoteUrl;

    /** Remote CMS authentication token or credentials */
    private String remoteToken;

    /** Default security parent ID for published content */
    private String defaultSecurityParentId = "PolopolyPost.d";

    /** Maximum image width for resizing during publish */
    private int maxImageWidth = 900;

    /** Image quality (0.0-1.0) for JPEG compression during publish */
    private double imageQuality = 0.75;

    /** Whether to prefer insert parent ID over security parent */
    private boolean preferInsertParentId = false;

    /** Whether to ignore authentication failures (continue publishing) */
    private boolean ignoreAuthenticationFailed = false;

    /** Status values that should not be published, keyed by content type */
    private Map<String, List<String>> nonPublishableStatus = new HashMap<>();

    /** Web status values that should not be published, keyed by content type */
    private Map<String, List<String>> nonPublishableWebStatus = new HashMap<>();

    /** Content types where private content should be published as empty */
    private List<String> privateContentAsEmpty = new ArrayList<>();

    // Getters/setters

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRemoteUrl() { return remoteUrl; }
    public void setRemoteUrl(String remoteUrl) { this.remoteUrl = remoteUrl; }

    public String getRemoteToken() { return remoteToken; }
    public void setRemoteToken(String remoteToken) { this.remoteToken = remoteToken; }

    public String getDefaultSecurityParentId() { return defaultSecurityParentId; }
    public void setDefaultSecurityParentId(String v) { this.defaultSecurityParentId = v; }

    public int getMaxImageWidth() { return maxImageWidth; }
    public void setMaxImageWidth(int maxImageWidth) { this.maxImageWidth = maxImageWidth; }

    public double getImageQuality() { return imageQuality; }
    public void setImageQuality(double imageQuality) { this.imageQuality = imageQuality; }

    public boolean isPreferInsertParentId() { return preferInsertParentId; }
    public void setPreferInsertParentId(boolean v) { this.preferInsertParentId = v; }

    public boolean isIgnoreAuthenticationFailed() { return ignoreAuthenticationFailed; }
    public void setIgnoreAuthenticationFailed(boolean v) { this.ignoreAuthenticationFailed = v; }

    public Map<String, List<String>> getNonPublishableStatus() { return nonPublishableStatus; }
    public void setNonPublishableStatus(Map<String, List<String>> v) { this.nonPublishableStatus = v; }

    public Map<String, List<String>> getNonPublishableWebStatus() { return nonPublishableWebStatus; }
    public void setNonPublishableWebStatus(Map<String, List<String>> v) { this.nonPublishableWebStatus = v; }

    public List<String> getPrivateContentAsEmpty() { return privateContentAsEmpty; }
    public void setPrivateContentAsEmpty(List<String> v) { this.privateContentAsEmpty = v; }
}
