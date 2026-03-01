package com.atex.desk.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LDAP authentication and user management service — ported from Polopoly's LdapUtil.
 * <p>
 * Supports: authentication via bind, DN resolution, user attribute sync,
 * group discovery (including nested), user provisioning (when write-enabled),
 * password changes, and LDAP group reload with timestamp-based change detection.
 */
@Component
@ConditionalOnProperty(name = "desk.ldap.enabled", havingValue = "true")
public class LdapAuthService
{
    private static final Logger LOG = LoggerFactory.getLogger(LdapAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LdapProperties properties;

    // Cached group data with timestamp-based invalidation
    private final ConcurrentHashMap<String, LdapGroup> groupCache = new ConcurrentHashMap<>();
    private volatile long lastGroupReload = 0;

    public LdapAuthService(LdapProperties properties)
    {
        this.properties = properties;
    }

    // ======== Authentication ========

    /**
     * Authenticate a user via LDAP bind.
     * Supports external authentication (SSO pre-authenticated) when configured.
     */
    public boolean authenticate(String loginName, String password)
    {
        // External authentication: if enabled and no password, trust pre-auth
        if (properties.isExternalAuthentication() && (password == null || password.isEmpty()))
        {
            return findUserDn(loginName) != null;
        }

        if (password == null || password.isEmpty())
        {
            return false;
        }

        try
        {
            String userDn = findUserDn(loginName);
            if (userDn == null)
            {
                LOG.debug("LDAP user not found: {}", loginName);
                return false;
            }

            // Attempt user bind
            Hashtable<String, String> env = createBaseEnvironment();
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, userDn);
            env.put(Context.SECURITY_CREDENTIALS, password);

            DirContext ctx = new InitialDirContext(env);
            ctx.close();
            return true;
        }
        catch (NamingException e)
        {
            LOG.debug("LDAP authentication failed for {}: {}", loginName, e.getMessage());
            return false;
        }
    }

    // ======== DN Resolution ========

    /**
     * Find the Distinguished Name for a user by login name.
     * Returns null if user not found.
     */
    public String findUserDn(String loginName)
    {
        try
        {
            return getDistinguishedNameForUser(loginName);
        }
        catch (NamingException e)
        {
            LOG.debug("Error looking up LDAP DN for {}: {}", loginName, e.getMessage());
            return null;
        }
    }

    /**
     * Resolve login name → LDAP DN via admin search.
     */
    public String getDistinguishedNameForUser(String loginName) throws NamingException
    {
        DirContext ctx = createAdminContext();
        try
        {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(1);

            String filter = buildUserSearchFilter(loginName);
            NamingEnumeration<SearchResult> results = ctx.search(
                properties.getSearchBase(), filter, controls);

            if (results.hasMore())
            {
                SearchResult result = results.next();
                String dn = result.getNameInNamespace();
                return normalizeDn(dn);
            }
            return null;
        }
        finally
        {
            ctx.close();
        }
    }

    /**
     * Resolve LDAP DN → login name.
     */
    public String getLoginNameForUser(String dn) throws NamingException
    {
        DirContext ctx = createAdminContext();
        try
        {
            Attributes attrs = ctx.getAttributes(dn,
                new String[]{properties.getLoginNameAttribute()});
            Attribute loginAttr = attrs.get(properties.getLoginNameAttribute());
            if (loginAttr != null)
            {
                return loginAttr.get().toString();
            }
            return null;
        }
        finally
        {
            ctx.close();
        }
    }

    // ======== User Attributes ========

    /**
     * Get mapped user attributes from LDAP.
     * Returns a map of application attribute names to values, using the configured
     * attribute mapping (desk.ldap.attribute-mapping.*).
     */
    public Map<String, Object> getUserAttributes(String loginName) throws NamingException
    {
        DirContext ctx = createAdminContext();
        try
        {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(1);

            // Request only the LDAP attributes we need
            if (!properties.getAttributeMapping().isEmpty())
            {
                controls.setReturningAttributes(
                    properties.getAttributeMapping().values().toArray(new String[0]));
            }

            String filter = buildUserSearchFilter(loginName);
            NamingEnumeration<SearchResult> results = ctx.search(
                properties.getSearchBase(), filter, controls);

            if (!results.hasMore())
            {
                return Map.of();
            }

            SearchResult result = results.next();
            Attributes ldapAttrs = result.getAttributes();
            Map<String, Object> mapped = new HashMap<>();

            for (Map.Entry<String, String> entry : properties.getAttributeMapping().entrySet())
            {
                String appAttr = entry.getKey();
                String ldapAttr = entry.getValue();
                Attribute attr = ldapAttrs.get(ldapAttr);

                if (attr != null)
                {
                    Object value = attr.get();
                    if (properties.getBinaryAttributes().contains(ldapAttr))
                    {
                        // Base64 encode binary attributes
                        if (value instanceof byte[] bytes)
                        {
                            value = Base64.getEncoder().encodeToString(bytes);
                        }
                    }
                    else if (properties.getBooleanAttributes().contains(ldapAttr))
                    {
                        // Normalize boolean
                        value = "TRUE".equalsIgnoreCase(value.toString());
                    }
                    mapped.put(appAttr, value);
                }
            }

            return mapped;
        }
        finally
        {
            ctx.close();
        }
    }

    /**
     * Get raw LDAP attributes for a user.
     */
    public Attributes getRawUserAttributes(String loginName) throws NamingException
    {
        DirContext ctx = createAdminContext();
        try
        {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(1);

            String filter = buildUserSearchFilter(loginName);
            NamingEnumeration<SearchResult> results = ctx.search(
                properties.getSearchBase(), filter, controls);

            if (results.hasMore())
            {
                return results.next().getAttributes();
            }
            return null;
        }
        finally
        {
            ctx.close();
        }
    }

    // ======== User Provisioning (write mode) ========

    /**
     * Modify an LDAP attribute for a user. Requires write-enabled.
     */
    public void modifyAttribute(String userDn, String attributeId, String value) throws NamingException
    {
        if (!properties.isWriteEnabled())
        {
            throw new NamingException("LDAP write not enabled");
        }

        DirContext ctx = createAdminContext();
        try
        {
            ModificationItem[] mods = new ModificationItem[1];
            BasicAttribute attr = new BasicAttribute(attributeId, value);
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, attr);
            ctx.modifyAttributes(userDn, mods);
        }
        finally
        {
            ctx.close();
        }
    }

    /**
     * Set a user's password in LDAP. Requires write-enabled.
     * Encodes using the configured password scheme.
     */
    public void setPassword(String userDn, String newPassword) throws NamingException
    {
        if (!properties.isWriteEnabled())
        {
            throw new NamingException("LDAP write not enabled");
        }

        String encoded = encodePassword(newPassword, properties.getPasswordScheme());
        modifyAttribute(userDn, properties.getUserPasswordAttribute(), encoded);
    }

    /**
     * Register a new user in LDAP. Requires write-enabled and canRegisterUsers.
     *
     * @return the DN of the created user
     */
    public String registerUser(String loginName, String password) throws NamingException
    {
        if (!properties.isWriteEnabled() || !properties.isCanRegisterUsers())
        {
            throw new NamingException("LDAP user registration not enabled");
        }

        // Check if user already exists
        String existing = getDistinguishedNameForUser(loginName);
        if (existing != null)
        {
            throw new NamingException("User already exists in LDAP: " + loginName);
        }

        DirContext ctx = createAdminContext();
        try
        {
            // Build DN for new user
            String dn = properties.getLoginNameAttribute() + "=" + escapeDnValue(loginName)
                + "," + properties.getSearchBase();

            // Build attributes
            BasicAttributes attrs = new BasicAttributes(true);
            attrs.put("objectClass", properties.getObjectClass());
            attrs.put(properties.getLoginNameAttribute(), loginName);

            if (password != null)
            {
                String encoded = encodePassword(password, properties.getPasswordScheme());
                attrs.put(properties.getUserPasswordAttribute(), encoded);
            }

            if (properties.getDomain() != null && properties.getDomainAttribute() != null)
            {
                attrs.put(properties.getDomainAttribute(), properties.getDomain());
            }

            ctx.createSubcontext(dn, attrs);
            return normalizeDn(dn);
        }
        finally
        {
            ctx.close();
        }
    }

    // ======== Group Operations ========

    /**
     * Get groups for a user from LDAP.
     */
    public List<String> getGroupsForUser(String loginName) throws NamingException
    {
        List<String> groups = new ArrayList<>();
        String groupBase = properties.getGroupSearchBase();
        if (groupBase == null || groupBase.isEmpty())
        {
            return groups;
        }

        String userDn = getDistinguishedNameForUser(loginName);
        if (userDn == null)
        {
            return groups;
        }

        DirContext ctx = createAdminContext();
        try
        {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{properties.getGroupNameAttribute()});

            String filter = buildGroupMemberFilter(userDn);
            NamingEnumeration<SearchResult> results = ctx.search(groupBase, filter, controls);

            while (results.hasMore())
            {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();
                Attribute cn = attrs.get(properties.getGroupNameAttribute());
                if (cn != null)
                {
                    groups.add(cn.get().toString());
                }
            }
        }
        finally
        {
            ctx.close();
        }

        return groups;
    }

    /**
     * Get all LDAP group DNs.
     */
    public Set<String> getAllGroups() throws NamingException
    {
        Set<String> groups = new HashSet<>();
        String groupBase = properties.getGroupSearchBase();
        if (groupBase == null || groupBase.isEmpty())
        {
            return groups;
        }

        DirContext ctx = createAdminContext();
        try
        {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            String filter = buildGroupSearchFilter();
            NamingEnumeration<SearchResult> results = ctx.search(groupBase, filter, controls);

            while (results.hasMore())
            {
                SearchResult result = results.next();
                groups.add(normalizeDn(result.getNameInNamespace()));
            }
        }
        finally
        {
            ctx.close();
        }

        return groups;
    }

    /**
     * Get group details by DN, with caching and timestamp-based invalidation.
     */
    public LdapGroup getGroupAttributes(String groupDn) throws NamingException
    {
        // Check cache
        LdapGroup cached = groupCache.get(normalizeDn(groupDn));
        if (cached != null && !isGroupStale(cached))
        {
            return cached;
        }

        DirContext ctx = createAdminContext();
        try
        {
            Attributes attrs = ctx.getAttributes(groupDn);
            LdapGroup group = new LdapGroup();

            // Group name
            Attribute nameAttr = attrs.get(properties.getGroupNameAttribute());
            if (nameAttr != null)
            {
                group.setGroupName(nameAttr.get().toString());
            }

            // Members
            Attribute memberAttr = attrs.get(properties.getGroupMemberAttribute());
            if (memberAttr != null)
            {
                NamingEnumeration<?> members = memberAttr.getAll();
                while (members.hasMore())
                {
                    String memberDn = normalizeDn(members.next().toString());
                    if (properties.isNestedGroupsSupported() && isLdapGroup(memberDn))
                    {
                        group.getNestedGroups().add(memberDn);
                    }
                    else
                    {
                        group.getUsers().add(memberDn);
                    }
                }
            }

            // Modify timestamp for change detection
            Attribute modifyTs = attrs.get("modifyTimestamp");
            if (modifyTs != null)
            {
                group.setModifyTimestamp(parseTimestamp(modifyTs.get().toString()));
            }

            groupCache.put(normalizeDn(groupDn), group);
            return group;
        }
        finally
        {
            ctx.close();
        }
    }

    /**
     * Check if a DN represents an LDAP group.
     */
    public boolean isLdapGroup(String dn)
    {
        try
        {
            DirContext ctx = createAdminContext();
            try
            {
                Attributes attrs = ctx.getAttributes(dn, new String[]{"objectClass"});
                Attribute objectClass = attrs.get("objectClass");
                if (objectClass != null)
                {
                    NamingEnumeration<?> values = objectClass.getAll();
                    while (values.hasMore())
                    {
                        if (properties.getGroupObjectClass()
                            .equalsIgnoreCase(values.next().toString()))
                        {
                            return true;
                        }
                    }
                }
            }
            finally
            {
                ctx.close();
            }
        }
        catch (NamingException e)
        {
            LOG.debug("Error checking if DN is group: {}", dn);
        }
        return false;
    }

    /**
     * Check if LDAP entry has been modified since a given timestamp.
     */
    public boolean isLdapEntryModified(String dn, long sinceTimestamp)
    {
        try
        {
            DirContext ctx = createAdminContext();
            try
            {
                Attributes attrs = ctx.getAttributes(dn,
                    new String[]{"modifyTimestamp", "createTimestamp"});
                Attribute modifyTs = attrs.get("modifyTimestamp");
                if (modifyTs == null)
                {
                    modifyTs = attrs.get("createTimestamp");
                }
                if (modifyTs != null)
                {
                    long ts = parseTimestamp(modifyTs.get().toString());
                    return ts > sinceTimestamp;
                }
            }
            finally
            {
                ctx.close();
            }
        }
        catch (NamingException e)
        {
            LOG.debug("Error checking LDAP entry modification: {}", dn);
        }
        // If we can't determine, assume modified
        return true;
    }

    /**
     * Force reload of all cached groups.
     */
    public void reloadGroups()
    {
        groupCache.clear();
        lastGroupReload = System.currentTimeMillis();
    }

    // ======== Configuration queries ========

    public boolean isConfigured()
    {
        return properties.isEnabled()
            && properties.getProviderUrl() != null
            && !properties.getProviderUrl().isEmpty();
    }

    public boolean isConfiguredWithGroups()
    {
        return isConfigured()
            && properties.getGroupSearchBase() != null
            && !properties.getGroupSearchBase().isEmpty();
    }

    public boolean isWriteEnabled()
    {
        return properties.isWriteEnabled();
    }

    // ======== Password encoding (for LDAP writes) ========

    /**
     * Encode a password using the specified scheme for LDAP storage.
     */
    public String encodePassword(String password, String scheme)
    {
        try
        {
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            return switch (scheme.toUpperCase())
            {
                case "CLEARTEXT" -> "{CLEARTEXT}" + password;
                case "SHA" ->
                {
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    yield "{SHA}" + Base64.getEncoder().encodeToString(md.digest(passwordBytes));
                }
                case "SSHA" ->
                {
                    byte[] salt = new byte[8];
                    RANDOM.nextBytes(salt);
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    md.update(passwordBytes);
                    md.update(salt);
                    byte[] digest = md.digest();
                    byte[] combined = new byte[digest.length + salt.length];
                    System.arraycopy(digest, 0, combined, 0, digest.length);
                    System.arraycopy(salt, 0, combined, digest.length, salt.length);
                    yield "{SSHA}" + Base64.getEncoder().encodeToString(combined);
                }
                case "MD5" ->
                {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    yield "{MD5}" + Base64.getEncoder().encodeToString(md.digest(passwordBytes));
                }
                case "SMD5" ->
                {
                    byte[] salt = new byte[8];
                    RANDOM.nextBytes(salt);
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(passwordBytes);
                    md.update(salt);
                    byte[] digest = md.digest();
                    byte[] combined = new byte[digest.length + salt.length];
                    System.arraycopy(digest, 0, combined, 0, digest.length);
                    System.arraycopy(salt, 0, combined, digest.length, salt.length);
                    yield "{SMD5}" + Base64.getEncoder().encodeToString(combined);
                }
                default -> "{CLEARTEXT}" + password;
            };
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to encode password with scheme: " + scheme, e);
        }
    }

    // ======== Internal helpers ========

    private DirContext createAdminContext() throws NamingException
    {
        Hashtable<String, String> env = createBaseEnvironment();
        if (properties.getAdminDn() != null && !properties.getAdminDn().isEmpty())
        {
            env.put(Context.SECURITY_AUTHENTICATION, properties.getSecurityAuthentication());
            env.put(Context.SECURITY_PRINCIPAL, properties.getAdminDn());
            env.put(Context.SECURITY_CREDENTIALS,
                properties.getAdminPassword() != null ? properties.getAdminPassword() : "");
        }
        return new InitialDirContext(env);
    }

    private Hashtable<String, String> createBaseEnvironment()
    {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, properties.getInitialContextFactory());
        env.put(Context.PROVIDER_URL, properties.getProviderUrl());

        if (properties.getReferral() != null && !properties.getReferral().isEmpty())
        {
            env.put(Context.REFERRAL, properties.getReferral());
        }
        if (properties.getSecurityProtocol() != null && !properties.getSecurityProtocol().isEmpty())
        {
            env.put(Context.SECURITY_PROTOCOL, properties.getSecurityProtocol());
        }
        if (properties.isConnectionPool())
        {
            env.put("com.sun.jndi.ldap.connect.pool", "true");
        }
        return env;
    }

    private String buildUserSearchFilter(String loginName)
    {
        StringBuilder filter = new StringBuilder();
        filter.append("(&(objectClass=").append(properties.getObjectClass()).append(")(")
            .append(properties.getLoginNameAttribute()).append("=")
            .append(escapeLdapFilter(loginName)).append(")");

        if (properties.getAdvancedQuery() != null && !properties.getAdvancedQuery().isEmpty())
        {
            filter.append(properties.getAdvancedQuery());
        }

        filter.append(")");
        return filter.toString();
    }

    private String buildGroupMemberFilter(String memberDn)
    {
        StringBuilder filter = new StringBuilder();
        filter.append("(&(objectClass=").append(properties.getGroupObjectClass()).append(")(")
            .append(properties.getGroupMemberAttribute()).append("=")
            .append(escapeLdapFilter(memberDn)).append(")");

        if (properties.getGroupAdvancedQuery() != null
            && !properties.getGroupAdvancedQuery().isEmpty())
        {
            filter.append(properties.getGroupAdvancedQuery());
        }

        filter.append(")");
        return filter.toString();
    }

    private String buildGroupSearchFilter()
    {
        StringBuilder filter = new StringBuilder();
        filter.append("(objectClass=").append(properties.getGroupObjectClass()).append(")");

        if (properties.getGroupAdvancedQuery() != null
            && !properties.getGroupAdvancedQuery().isEmpty())
        {
            filter = new StringBuilder("(&")
                .append(filter)
                .append(properties.getGroupAdvancedQuery())
                .append(")");
        }

        return filter.toString();
    }

    private String normalizeDn(String dn)
    {
        if (dn == null) return null;
        if (properties.isCaseInsensitive())
        {
            return dn.toLowerCase();
        }
        return dn;
    }

    private boolean isGroupStale(LdapGroup group)
    {
        long now = System.currentTimeMillis();
        return (now - lastGroupReload) > properties.getGroupReloadInterval();
    }

    private long parseTimestamp(String ldapTimestamp)
    {
        // LDAP generalized time format: YYYYMMDDHHmmss.fZ
        try
        {
            String cleaned = ldapTimestamp.replaceAll("[.][0-9]+", "")
                .replace("Z", "");
            if (cleaned.length() >= 14)
            {
                int year = Integer.parseInt(cleaned.substring(0, 4));
                int month = Integer.parseInt(cleaned.substring(4, 6));
                int day = Integer.parseInt(cleaned.substring(6, 8));
                int hour = Integer.parseInt(cleaned.substring(8, 10));
                int min = Integer.parseInt(cleaned.substring(10, 12));
                int sec = Integer.parseInt(cleaned.substring(12, 14));
                java.time.LocalDateTime ldt = java.time.LocalDateTime.of(
                    year, month, day, hour, min, sec);
                return ldt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            }
        }
        catch (Exception e)
        {
            LOG.debug("Failed to parse LDAP timestamp: {}", ldapTimestamp);
        }
        return 0;
    }

    private static String escapeLdapFilter(String input)
    {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray())
        {
            switch (c)
            {
                case '\\' -> sb.append("\\5c");
                case '*' -> sb.append("\\2a");
                case '(' -> sb.append("\\28");
                case ')' -> sb.append("\\29");
                case '\0' -> sb.append("\\00");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeDnValue(String value)
    {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
                case ',' -> sb.append("\\,");
                case '+' -> sb.append("\\+");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '<' -> sb.append("\\<");
                case '>' -> sb.append("\\>");
                case ';' -> sb.append("\\;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ======== Inner classes ========

    /**
     * Represents an LDAP group with its members — mirrors Polopoly's LdapUtil.LdapGroup.
     */
    public static class LdapGroup
    {
        private String groupName;
        private Set<String> nestedGroups = new HashSet<>();
        private Set<String> users = new HashSet<>();
        private long modifyTimestamp;

        public String getGroupName()
        {
            return groupName;
        }

        public void setGroupName(String groupName)
        {
            this.groupName = groupName;
        }

        public Set<String> getNestedGroups()
        {
            return nestedGroups;
        }

        public void setNestedGroups(Set<String> nestedGroups)
        {
            this.nestedGroups = nestedGroups;
        }

        public Set<String> getUsers()
        {
            return users;
        }

        public void setUsers(Set<String> users)
        {
            this.users = users;
        }

        public long getModifyTimestamp()
        {
            return modifyTimestamp;
        }

        public void setModifyTimestamp(long modifyTimestamp)
        {
            this.modifyTimestamp = modifyTimestamp;
        }
    }
}
