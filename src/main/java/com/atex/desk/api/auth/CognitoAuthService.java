package com.atex.desk.api.auth;

import com.atex.desk.api.entity.AppUser;
import com.atex.desk.api.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AWS Cognito authentication service — ported from Polopoly's CognitoSecurityService.
 * <p>
 * Supports: username/password auth (Admin API), OAuth authorization code flow,
 * user attribute sync, group discovery with caching, auto CM user creation,
 * and login name mapping.
 */
@Component
@ConditionalOnProperty(name = "desk.cognito.enabled", havingValue = "true")
public class CognitoAuthService
{
    private static final Logger LOG = LoggerFactory.getLogger(CognitoAuthService.class);

    private final CognitoProperties properties;
    private final CognitoIdentityProviderClient cognitoClient;
    private final AppUserRepository appUserRepository;
    private final CognitoTokenVerifier tokenVerifier;

    // Caches with TTL
    private final ConcurrentHashMap<String, CachedEntry<String>> loginNameCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<Map<String, String>>> userAttributeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedEntry<List<String>>> groupCache = new ConcurrentHashMap<>();

    public CognitoAuthService(CognitoProperties properties, AppUserRepository appUserRepository)
    {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
        this.cognitoClient = buildClient();
        this.tokenVerifier = new CognitoTokenVerifier(properties);
    }

    private CognitoIdentityProviderClient buildClient()
    {
        var builder = CognitoIdentityProviderClient.builder()
            .region(Region.of(properties.getRegion()));
        if (properties.getAccessKey() != null && !properties.getAccessKey().isEmpty())
        {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
        }
        return builder.build();
    }

    // ======== Authentication ========

    /**
     * Authenticate a user via Cognito AdminInitiateAuth (ADMIN_USER_PASSWORD_AUTH).
     */
    public boolean authenticate(String username, String password)
    {
        try
        {
            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                .userPoolId(properties.getUserPoolId())
                .clientId(properties.getClientId())
                .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                .authParameters(Map.of(
                    "USERNAME", username,
                    "PASSWORD", password
                ))
                .build();

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(authRequest);
            return response.authenticationResult() != null;
        }
        catch (Exception e)
        {
            LOG.debug("Cognito authentication failed for {}: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Authenticate via OAuth authorization code exchange.
     * Calls Cognito token endpoint to exchange code for tokens.
     *
     * @return CognitoUser with tokens and user info, or null on failure
     */
    public CognitoUser exchangeCodeForToken(String code, String callbackUrl)
    {
        try
        {
            String tokenEndpoint = properties.getTokenEndpoint();

            // Build form body
            StringBuilder body = new StringBuilder();
            body.append("grant_type=authorization_code");
            body.append("&code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
            body.append("&client_id=").append(URLEncoder.encode(properties.getClientId(), StandardCharsets.UTF_8));
            body.append("&redirect_uri=").append(URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8));

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            // Add client authentication if client secret is configured
            if (properties.getClientSecret() != null && !properties.getClientSecret().isEmpty())
            {
                String credentials = properties.getClientId() + ":" + properties.getClientSecret();
                String encoded = Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));
                reqBuilder.header("Authorization", "Basic " + encoded);
            }

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
            {
                LOG.warn("Cognito token exchange failed: {} {}", response.statusCode(), response.body());
                return null;
            }

            // Parse JSON response using Gson (already on classpath)
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body())
                .getAsJsonObject();

            CognitoUser user = new CognitoUser();
            if (json.has("id_token"))
            {
                user.setIdToken(json.get("id_token").getAsString());
            }
            if (json.has("access_token"))
            {
                user.setAccessToken(json.get("access_token").getAsString());
            }
            if (json.has("refresh_token"))
            {
                user.setRefreshToken(json.get("refresh_token").getAsString());
            }

            // Verify the ID token signature and extract claims
            if (user.getIdToken() != null)
            {
                try
                {
                    CognitoUser verified = tokenVerifier.verify(user.getIdToken());
                    user.setUsername(verified.getUsername());
                    user.setEmail(verified.getEmail());
                    user.setGroups(verified.getGroups());
                }
                catch (Exception e)
                {
                    // Fallback to base64 decode — token endpoint already validated
                    LOG.debug("Token verification failed, falling back to base64 decode: {}", e.getMessage());
                    decodeIdToken(user);
                }
            }

            return user;
        }
        catch (IOException | InterruptedException e)
        {
            LOG.error("Error exchanging code for token: {}", e.getMessage(), e);
            return null;
        }
    }

    // ======== OAuth URLs ========

    /**
     * Build the Cognito hosted UI URL for OAuth login.
     */
    public String getOAuthUrl(String callbackUrl)
    {
        if (properties.getDomain() == null || properties.getDomain().isEmpty())
        {
            return null;
        }

        String responseType = switch (properties.getAuthFlow().toLowerCase())
        {
            case "token" -> "token";
            case "id_token" -> "id_token";
            default -> "code";
        };

        return properties.getAuthorizeEndpoint()
            + "?client_id=" + URLEncoder.encode(properties.getClientId(), StandardCharsets.UTF_8)
            + "&response_type=" + responseType
            + "&scope=" + URLEncoder.encode(properties.getScope(), StandardCharsets.UTF_8)
            + "&redirect_uri=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
    }

    // ======== User existence & lookup ========

    /**
     * Check if a user exists in Cognito.
     */
    public boolean userExists(String username)
    {
        try
        {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder()
                .userPoolId(properties.getUserPoolId())
                .username(username)
                .build());
            return true;
        }
        catch (UserNotFoundException e)
        {
            return false;
        }
        catch (Exception e)
        {
            LOG.warn("Error checking Cognito user existence for {}: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Get the login name for a Cognito user, applying loginNamesMap mapping.
     * If the Cognito username starts with a mapped prefix, the corresponding
     * Cognito attribute is used as the login name.
     */
    public String getLoginName(String username)
    {
        // Check cache
        CachedEntry<String> cached = loginNameCache.get(username);
        if (cached != null && !cached.isExpired(properties.getLoginNameCacheTtl()))
        {
            return cached.value;
        }

        String loginName = username;
        Map<String, String> map = properties.getLoginNamesMap();

        if (!map.isEmpty())
        {
            for (Map.Entry<String, String> entry : map.entrySet())
            {
                String prefix = entry.getKey();
                String attribute = entry.getValue();
                if (username.startsWith(prefix + ":") || username.startsWith(prefix + "_"))
                {
                    // Fetch the attribute from Cognito
                    try
                    {
                        AdminGetUserResponse userResp = cognitoClient.adminGetUser(
                            AdminGetUserRequest.builder()
                                .userPoolId(properties.getUserPoolId())
                                .username(username)
                                .build());

                        for (AttributeType attr : userResp.userAttributes())
                        {
                            if (attribute.equals(attr.name()))
                            {
                                loginName = attr.value();
                                break;
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        LOG.warn("Error fetching attribute {} for user {}: {}",
                            attribute, username, e.getMessage());
                    }
                    break;
                }
            }
        }

        loginNameCache.put(username, new CachedEntry<>(loginName));
        evictIfNeeded(loginNameCache, properties.getLoginNameCacheSize());
        return loginName;
    }

    // ======== User Attributes ========

    /**
     * Get mapped user attributes from Cognito.
     */
    public Map<String, String> getMappedUserAttributes(String username)
    {
        // Check cache
        CachedEntry<Map<String, String>> cached = userAttributeCache.get(username);
        if (cached != null && !cached.isExpired(properties.getUserAttributeCacheTtl()))
        {
            return cached.value;
        }

        Map<String, String> result = new HashMap<>();
        Map<String, String> mapping = properties.getUserAttributes();

        if (mapping.isEmpty())
        {
            return result;
        }

        try
        {
            AdminGetUserResponse userResp = cognitoClient.adminGetUser(
                AdminGetUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(username)
                    .build());

            for (AttributeType attr : userResp.userAttributes())
            {
                for (Map.Entry<String, String> entry : mapping.entrySet())
                {
                    if (entry.getValue().equals(attr.name()))
                    {
                        result.put(entry.getKey(), attr.value());
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn("Error fetching Cognito attributes for {}: {}", username, e.getMessage());
        }

        userAttributeCache.put(username, new CachedEntry<>(result));
        evictIfNeeded(userAttributeCache, properties.getUserAttributeCacheSize());
        return result;
    }

    /**
     * Get raw user attributes from Cognito.
     */
    public AdminGetUserResponse getRawUserAttributes(String username)
    {
        return cognitoClient.adminGetUser(AdminGetUserRequest.builder()
            .userPoolId(properties.getUserPoolId())
            .username(username)
            .build());
    }

    // ======== Group Operations ========

    /**
     * Get groups for a user from Cognito (paginated, cached).
     */
    public List<String> getGroupsForUser(String username)
    {
        // Check cache
        CachedEntry<List<String>> cached = groupCache.get("user:" + username);
        if (cached != null && !cached.isExpired(properties.getUserAttributeCacheTtl()))
        {
            return cached.value;
        }

        List<String> groups = new ArrayList<>();
        String nextToken = null;

        try
        {
            do
            {
                AdminListGroupsForUserRequest.Builder reqBuilder = AdminListGroupsForUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(username)
                    .limit(60);

                if (nextToken != null)
                {
                    reqBuilder.nextToken(nextToken);
                }

                AdminListGroupsForUserResponse response = cognitoClient.adminListGroupsForUser(
                    reqBuilder.build());

                for (GroupType group : response.groups())
                {
                    String name = group.groupName();
                    if (shouldIncludeGroup(name))
                    {
                        groups.add(name);
                    }
                }
                nextToken = response.nextToken();
            }
            while (nextToken != null);
        }
        catch (Exception e)
        {
            LOG.warn("Error fetching groups for Cognito user {}: {}", username, e.getMessage());
        }

        groupCache.put("user:" + username, new CachedEntry<>(groups));
        return groups;
    }

    /**
     * Get all groups from Cognito user pool (paginated).
     */
    public List<String> getAllGroups()
    {
        // Check cache
        CachedEntry<List<String>> cached = groupCache.get("all");
        if (cached != null && !cached.isExpired(properties.getUserAttributeCacheTtl()))
        {
            return cached.value;
        }

        List<String> groups = new ArrayList<>();
        String nextToken = null;

        try
        {
            do
            {
                ListGroupsRequest.Builder reqBuilder = ListGroupsRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .limit(60);

                if (nextToken != null)
                {
                    reqBuilder.nextToken(nextToken);
                }

                ListGroupsResponse response = cognitoClient.listGroups(reqBuilder.build());

                for (GroupType group : response.groups())
                {
                    String name = group.groupName();
                    if (shouldIncludeGroup(name))
                    {
                        groups.add(name);
                    }
                }
                nextToken = response.nextToken();
            }
            while (nextToken != null);
        }
        catch (Exception e)
        {
            LOG.warn("Error listing Cognito groups: {}", e.getMessage());
        }

        groupCache.put("all", new CachedEntry<>(groups));
        return groups;
    }

    /**
     * Get members of a specific Cognito group (paginated).
     */
    public List<String> getMembersByGroup(String groupName)
    {
        List<String> members = new ArrayList<>();
        String nextToken = null;

        try
        {
            do
            {
                ListUsersInGroupRequest.Builder reqBuilder = ListUsersInGroupRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .groupName(groupName)
                    .limit(60);

                if (nextToken != null)
                {
                    reqBuilder.nextToken(nextToken);
                }

                ListUsersInGroupResponse response = cognitoClient.listUsersInGroup(reqBuilder.build());

                for (UserType user : response.users())
                {
                    members.add(user.username());
                }
                nextToken = response.nextToken();
            }
            while (nextToken != null);
        }
        catch (Exception e)
        {
            LOG.warn("Error listing members of Cognito group {}: {}", groupName, e.getMessage());
        }

        return members;
    }

    // ======== Auto CM User ========

    /**
     * Ensure a Cognito user exists in the local DB.
     * Creates the user record if autoCmUser is enabled and user doesn't exist.
     *
     * @return the login name (which is the DB PK)
     */
    public String ensureLocalUser(String cognitoUsername)
    {
        String loginName = getLoginName(cognitoUsername);

        if (appUserRepository.findByLoginName(loginName).isPresent())
        {
            return loginName;
        }

        if (!properties.isAutoCmUser())
        {
            return null;
        }

        // Auto-create local user
        AppUser user = new AppUser();
        user.setLoginName(loginName);
        user.setPasswordHash("{COGNITOUSER}");
        user.setRegTime((int) (System.currentTimeMillis() / 1000));
        user.setIsRemoteUser(1);
        user.setRemoteServiceId("cognito");
        user.setActive(1);
        appUserRepository.save(user);

        LOG.info("Auto-created local user for Cognito user: {}", loginName);
        return loginName;
    }

    // ======== Token Verification ========

    /**
     * Verify a Cognito JWT token with full signature validation.
     *
     * @param token the raw JWT string (id_token or access_token)
     * @return CognitoUser with username, email, and groups
     * @throws InvalidTokenException if the token is invalid or verification fails
     */
    public CognitoUser verifyToken(String token) throws InvalidTokenException
    {
        return tokenVerifier.verify(token);
    }

    /**
     * Handle an OAuth callback URL — supports both authorization code flow and
     * implicit (token/id_token) flow.
     * <p>
     * For code flow: URL contains {@code ?code=xxx} → exchanges code for tokens.
     * For implicit flow: URL contains {@code #id_token=xxx} or {@code #access_token=xxx}
     * → verifies the token directly.
     *
     * @param url         the full callback URL with query or fragment parameters
     * @param callbackUrl the redirect_uri used for the code exchange
     * @return CognitoUser, or null on failure
     */
    public CognitoUser verifyOAuthUrl(String url, String callbackUrl)
    {
        // Try fragment parameters first (implicit flow)
        Map<String, String> fragments = parseFragments(url);
        if (fragments.containsKey("id_token"))
        {
            try
            {
                return verifyToken(fragments.get("id_token"));
            }
            catch (InvalidTokenException e)
            {
                LOG.warn("Failed to verify id_token from fragment: {}", e.getMessage());
                return null;
            }
        }
        if (fragments.containsKey("access_token"))
        {
            try
            {
                return verifyToken(fragments.get("access_token"));
            }
            catch (InvalidTokenException e)
            {
                LOG.warn("Failed to verify access_token from fragment: {}", e.getMessage());
                return null;
            }
        }

        // Try query parameters (code flow)
        Map<String, String> query = parseQueryString(url);
        if (query.containsKey("code"))
        {
            return exchangeCodeForToken(query.get("code"), callbackUrl);
        }

        LOG.warn("OAuth URL contains neither code nor token: {}", url);
        return null;
    }

    // ======== User Lifecycle Management ========

    /**
     * Get the last modified date for a Cognito user.
     *
     * @return last modified time in milliseconds, or -1 if user not found
     */
    public long getLastModified(String username)
    {
        try
        {
            AdminGetUserResponse response = cognitoClient.adminGetUser(
                AdminGetUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(username)
                    .build());

            Instant lastModified = response.userLastModifiedDate();
            if (lastModified != null)
            {
                return lastModified.toEpochMilli();
            }
            Instant created = response.userCreateDate();
            if (created != null)
            {
                return created.toEpochMilli();
            }
            return -1;
        }
        catch (UserNotFoundException e)
        {
            return -1;
        }
        catch (Exception e)
        {
            LOG.warn("Error getting last modified for Cognito user {}: {}", username, e.getMessage());
            return -1;
        }
    }

    /**
     * Create (invite) a user in Cognito with the given username and email.
     * Cognito sends an invitation email with a temporary password.
     */
    public void inviteUser(String username, String email)
    {
        cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
            .userPoolId(properties.getUserPoolId())
            .username(username)
            .userAttributes(
                AttributeType.builder().name("email").value(email).build(),
                AttributeType.builder().name("email_verified").value("true").build()
            )
            .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
            .build());

        LOG.info("Invited Cognito user: {} ({})", username, email);
    }

    /**
     * Disable a user in Cognito (prevents sign-in).
     */
    public void disableUser(String username)
    {
        cognitoClient.adminDisableUser(AdminDisableUserRequest.builder()
            .userPoolId(properties.getUserPoolId())
            .username(username)
            .build());

        LOG.info("Disabled Cognito user: {}", username);
    }

    /**
     * Enable a previously disabled user in Cognito.
     */
    public void enableUser(String username)
    {
        cognitoClient.adminEnableUser(AdminEnableUserRequest.builder()
            .userPoolId(properties.getUserPoolId())
            .username(username)
            .build());

        LOG.info("Enabled Cognito user: {}", username);
    }

    /**
     * Delete a user from Cognito.
     */
    public void deleteUser(String username)
    {
        cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
            .userPoolId(properties.getUserPoolId())
            .username(username)
            .build());

        LOG.info("Deleted Cognito user: {}", username);
    }

    /**
     * Check if a group exists in Cognito.
     */
    public boolean groupExists(String groupName)
    {
        try
        {
            cognitoClient.getGroup(GetGroupRequest.builder()
                .userPoolId(properties.getUserPoolId())
                .groupName(groupName)
                .build());
            return true;
        }
        catch (ResourceNotFoundException e)
        {
            return false;
        }
        catch (Exception e)
        {
            LOG.warn("Error checking Cognito group existence for {}: {}", groupName, e.getMessage());
            return false;
        }
    }

    // ======== URL Parsing Utilities ========

    /**
     * Parse query parameters from a URL string.
     *
     * @return map of parameter name → decoded value
     */
    static Map<String, String> parseQueryString(String url)
    {
        Map<String, String> params = new LinkedHashMap<>();
        int queryStart = url.indexOf('?');
        if (queryStart < 0)
        {
            return params;
        }
        String query = url.substring(queryStart + 1);
        // Strip fragment if present
        int fragStart = query.indexOf('#');
        if (fragStart >= 0)
        {
            query = query.substring(0, fragStart);
        }
        parseParams(query, params);
        return params;
    }

    /**
     * Parse fragment parameters from a URL string.
     *
     * @return map of parameter name → decoded value
     */
    static Map<String, String> parseFragments(String url)
    {
        Map<String, String> params = new LinkedHashMap<>();
        int fragStart = url.indexOf('#');
        if (fragStart < 0)
        {
            return params;
        }
        parseParams(url.substring(fragStart + 1), params);
        return params;
    }

    private static void parseParams(String paramString, Map<String, String> target)
    {
        if (paramString == null || paramString.isEmpty())
        {
            return;
        }
        for (String pair : paramString.split("&"))
        {
            int eq = pair.indexOf('=');
            if (eq > 0)
            {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                target.put(key, value);
            }
            else if (!pair.isEmpty())
            {
                target.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
    }

    // ======== Internal helpers ========

    /**
     * Decode a JWT ID token to extract username and groups via base64 payload decode.
     * No signature verification — use {@link #verifyToken(String)} for verified decode.
     */
    public void decodeIdToken(CognitoUser user)
    {
        try
        {
            String[] parts = user.getIdToken().split("\\.");
            if (parts.length < 2) return;

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]),
                StandardCharsets.UTF_8);
            com.google.gson.JsonObject claims = com.google.gson.JsonParser.parseString(payload)
                .getAsJsonObject();

            // Username: try cognito:username first, then preferred_username, then sub
            if (claims.has("cognito:username"))
            {
                user.setUsername(claims.get("cognito:username").getAsString());
            }
            else if (claims.has("preferred_username"))
            {
                user.setUsername(claims.get("preferred_username").getAsString());
            }
            else if (claims.has("sub"))
            {
                user.setUsername(claims.get("sub").getAsString());
            }

            // Email
            if (claims.has("email"))
            {
                user.setEmail(claims.get("email").getAsString());
            }

            // Groups
            if (claims.has("cognito:groups"))
            {
                List<String> groups = new ArrayList<>();
                for (com.google.gson.JsonElement elem : claims.get("cognito:groups").getAsJsonArray())
                {
                    String name = elem.getAsString();
                    if (shouldIncludeGroup(name))
                    {
                        groups.add(name);
                    }
                }
                user.setGroups(groups);
            }
        }
        catch (Exception e)
        {
            LOG.warn("Error decoding ID token: {}", e.getMessage());
        }
    }

    private boolean shouldIncludeGroup(String groupName)
    {
        if (groupName == null) return false;
        if (groupName.length() > properties.getMaxGroupNameSize())
        {
            if (properties.isIgnoreLongNameGroups())
            {
                return false;
            }
            LOG.debug("Group name exceeds max size: {}", groupName);
        }
        return true;
    }

    private <T> void evictIfNeeded(ConcurrentHashMap<String, CachedEntry<T>> cache, int maxSize)
    {
        if (cache.size() > maxSize)
        {
            // Simple eviction: remove oldest entries
            var entries = new ArrayList<>(cache.entrySet());
            entries.sort((a, b) -> Long.compare(a.getValue().timestamp, b.getValue().timestamp));
            int toRemove = cache.size() - maxSize;
            for (int i = 0; i < toRemove && i < entries.size(); i++)
            {
                cache.remove(entries.get(i).getKey());
            }
        }
    }

    // ======== Inner classes ========

    private static class CachedEntry<T>
    {
        final T value;
        final long timestamp;

        CachedEntry(T value)
        {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlSeconds)
        {
            return System.currentTimeMillis() - timestamp > TimeUnit.SECONDS.toMillis(ttlSeconds);
        }
    }

    /**
     * Represents a Cognito user decoded from JWT tokens.
     * Mirrors Polopoly's CognitoUser.
     */
    public static class CognitoUser
    {
        private String username;
        private String email;
        private List<String> groups = new ArrayList<>();
        private String accessToken;
        private String idToken;
        private String refreshToken;

        public String getUsername()
        {
            return username;
        }

        public void setUsername(String username)
        {
            this.username = username;
        }

        public String getEmail()
        {
            return email;
        }

        public void setEmail(String email)
        {
            this.email = email;
        }

        public List<String> getGroups()
        {
            return groups;
        }

        public void setGroups(List<String> groups)
        {
            this.groups = groups;
        }

        public String getAccessToken()
        {
            return accessToken;
        }

        public void setAccessToken(String accessToken)
        {
            this.accessToken = accessToken;
        }

        public String getIdToken()
        {
            return idToken;
        }

        public void setIdToken(String idToken)
        {
            this.idToken = idToken;
        }

        public String getRefreshToken()
        {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken)
        {
            this.refreshToken = refreshToken;
        }
    }
}
