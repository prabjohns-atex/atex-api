package com.atex.desk.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Cognito configuration properties — ported from Polopoly's CognitoConfig.
 */
@ConfigurationProperties(prefix = "desk.cognito")
public class CognitoProperties
{
    private boolean enabled = false;
    private String userPoolId;
    private String clientId;
    private String clientSecret;
    private String region = "eu-west-1";
    private String accessKey;
    private String secretKey;
    private String domain;

    // OAuth settings
    private String authFlow = "code"; // code, token, id_token
    private String scope = "openid email profile";
    private String callbackUrl;

    // User management
    private boolean autoCmUser = false; // auto-create local DB users on first OAuth login
    private boolean useGroups = true;
    private int maxGroupNameSize = 64;
    private boolean ignoreLongNameGroups = false;

    // Login name mapping: prefix → cognito attribute
    // e.g., cognito=email (users whose username starts with "cognito:" use email as login name)
    private Map<String, String> loginNamesMap = new HashMap<>();

    // User attribute mapping: app attribute → cognito attribute
    // e.g., firstName=given_name, lastName=family_name
    private Map<String, String> userAttributes = new HashMap<>();

    // Cache settings
    private int loginNameCacheSize = 200;
    private long loginNameCacheTtl = 1800; // 30 minutes in seconds
    private int userAttributeCacheSize = 200;
    private long userAttributeCacheTtl = 300; // 5 minutes in seconds

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getUserPoolId()
    {
        return userPoolId;
    }

    public void setUserPoolId(String userPoolId)
    {
        this.userPoolId = userPoolId;
    }

    public String getClientId()
    {
        return clientId;
    }

    public void setClientId(String clientId)
    {
        this.clientId = clientId;
    }

    public String getClientSecret()
    {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret)
    {
        this.clientSecret = clientSecret;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public String getAccessKey()
    {
        return accessKey;
    }

    public void setAccessKey(String accessKey)
    {
        this.accessKey = accessKey;
    }

    public String getSecretKey()
    {
        return secretKey;
    }

    public void setSecretKey(String secretKey)
    {
        this.secretKey = secretKey;
    }

    public String getDomain()
    {
        return domain;
    }

    public void setDomain(String domain)
    {
        this.domain = domain;
    }

    public String getAuthFlow()
    {
        return authFlow;
    }

    public void setAuthFlow(String authFlow)
    {
        this.authFlow = authFlow;
    }

    public String getScope()
    {
        return scope;
    }

    public void setScope(String scope)
    {
        this.scope = scope;
    }

    public String getCallbackUrl()
    {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl)
    {
        this.callbackUrl = callbackUrl;
    }

    public boolean isAutoCmUser()
    {
        return autoCmUser;
    }

    public void setAutoCmUser(boolean autoCmUser)
    {
        this.autoCmUser = autoCmUser;
    }

    public boolean isUseGroups()
    {
        return useGroups;
    }

    public void setUseGroups(boolean useGroups)
    {
        this.useGroups = useGroups;
    }

    public int getMaxGroupNameSize()
    {
        return maxGroupNameSize;
    }

    public void setMaxGroupNameSize(int maxGroupNameSize)
    {
        this.maxGroupNameSize = maxGroupNameSize;
    }

    public boolean isIgnoreLongNameGroups()
    {
        return ignoreLongNameGroups;
    }

    public void setIgnoreLongNameGroups(boolean ignoreLongNameGroups)
    {
        this.ignoreLongNameGroups = ignoreLongNameGroups;
    }

    public Map<String, String> getLoginNamesMap()
    {
        return loginNamesMap;
    }

    public void setLoginNamesMap(Map<String, String> loginNamesMap)
    {
        this.loginNamesMap = loginNamesMap;
    }

    public Map<String, String> getUserAttributes()
    {
        return userAttributes;
    }

    public void setUserAttributes(Map<String, String> userAttributes)
    {
        this.userAttributes = userAttributes;
    }

    public int getLoginNameCacheSize()
    {
        return loginNameCacheSize;
    }

    public void setLoginNameCacheSize(int loginNameCacheSize)
    {
        this.loginNameCacheSize = loginNameCacheSize;
    }

    public long getLoginNameCacheTtl()
    {
        return loginNameCacheTtl;
    }

    public void setLoginNameCacheTtl(long loginNameCacheTtl)
    {
        this.loginNameCacheTtl = loginNameCacheTtl;
    }

    public int getUserAttributeCacheSize()
    {
        return userAttributeCacheSize;
    }

    public void setUserAttributeCacheSize(int userAttributeCacheSize)
    {
        this.userAttributeCacheSize = userAttributeCacheSize;
    }

    public long getUserAttributeCacheTtl()
    {
        return userAttributeCacheTtl;
    }

    public void setUserAttributeCacheTtl(long userAttributeCacheTtl)
    {
        this.userAttributeCacheTtl = userAttributeCacheTtl;
    }

    /**
     * Get the Cognito issuer URL for JWT validation.
     */
    public String getIssuerUrl()
    {
        return "https://cognito-idp." + region + ".amazonaws.com/" + userPoolId;
    }

    /**
     * Get the JWK endpoint URL.
     */
    public String getJwksUrl()
    {
        return getIssuerUrl() + "/.well-known/jwks.json";
    }

    /**
     * Get the token endpoint URL.
     */
    public String getTokenEndpoint()
    {
        String domainBase = getDomainBase();
        return domainBase + "/oauth2/token";
    }

    /**
     * Get the authorize endpoint URL.
     */
    public String getAuthorizeEndpoint()
    {
        String domainBase = getDomainBase();
        return domainBase + "/oauth2/authorize";
    }

    private String getDomainBase()
    {
        if (domain != null && domain.startsWith("http"))
        {
            return domain;
        }
        return "https://" + domain + ".auth." + region + ".amazoncognito.com";
    }
}
