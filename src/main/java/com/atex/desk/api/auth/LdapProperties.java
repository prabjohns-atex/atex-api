package com.atex.desk.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LDAP configuration properties — maps Polopoly's LdapUtil configuration.
 */
@ConfigurationProperties(prefix = "desk.ldap")
public class LdapProperties
{
    private boolean enabled = false;

    // Connection
    private String providerUrl;
    private String initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
    private String securityProtocol;
    private String securityAuthentication = "simple";
    private String referral = "follow";
    private boolean connectionPool = true;

    // Admin bind
    private String adminDn;
    private String adminPassword;

    // User search
    private String searchBase;
    private String loginNameAttribute = "uid";
    private String objectClass = "inetOrgPerson";
    private String advancedQuery;
    private String userPasswordAttribute = "userPassword";

    // Group search
    private String groupSearchBase;
    private String groupObjectClass = "groupOfNames";
    private String groupMemberAttribute = "member";
    private String groupNameAttribute = "cn";
    private String groupAdvancedQuery;
    private boolean nestedGroupsSupported = false;

    // Group reload
    private long groupReloadInterval = 3600000; // 1 hour in ms
    private String groupReloadTimeOfDay;

    // Behavior flags
    private boolean writeEnabled = false;
    private boolean canRegisterUsers = false;
    private boolean caseInsensitive = true;
    private boolean externalAuthentication = false;

    // Password scheme for writes: CLEARTEXT, SHA, SSHA, MD5, SMD5, OLDSHA
    private String passwordScheme = "SSHA";

    // Attribute mappings: application attribute name → LDAP attribute name
    // Format in properties: desk.ldap.attribute-mapping.firstName=givenName
    private Map<String, String> attributeMapping = new HashMap<>();

    // Binary attributes (base64 encode/decode)
    private Set<String> binaryAttributes = new HashSet<>();

    // Boolean attributes
    private Set<String> booleanAttributes = new HashSet<>();

    // Domain support
    private String domain;
    private String domainAttribute;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getProviderUrl()
    {
        return providerUrl;
    }

    public void setProviderUrl(String providerUrl)
    {
        this.providerUrl = providerUrl;
    }

    public String getInitialContextFactory()
    {
        return initialContextFactory;
    }

    public void setInitialContextFactory(String initialContextFactory)
    {
        this.initialContextFactory = initialContextFactory;
    }

    public String getSecurityProtocol()
    {
        return securityProtocol;
    }

    public void setSecurityProtocol(String securityProtocol)
    {
        this.securityProtocol = securityProtocol;
    }

    public String getSecurityAuthentication()
    {
        return securityAuthentication;
    }

    public void setSecurityAuthentication(String securityAuthentication)
    {
        this.securityAuthentication = securityAuthentication;
    }

    public String getReferral()
    {
        return referral;
    }

    public void setReferral(String referral)
    {
        this.referral = referral;
    }

    public boolean isConnectionPool()
    {
        return connectionPool;
    }

    public void setConnectionPool(boolean connectionPool)
    {
        this.connectionPool = connectionPool;
    }

    public String getAdminDn()
    {
        return adminDn;
    }

    public void setAdminDn(String adminDn)
    {
        this.adminDn = adminDn;
    }

    public String getAdminPassword()
    {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword)
    {
        this.adminPassword = adminPassword;
    }

    public String getSearchBase()
    {
        return searchBase;
    }

    public void setSearchBase(String searchBase)
    {
        this.searchBase = searchBase;
    }

    public String getLoginNameAttribute()
    {
        return loginNameAttribute;
    }

    public void setLoginNameAttribute(String loginNameAttribute)
    {
        this.loginNameAttribute = loginNameAttribute;
    }

    public String getObjectClass()
    {
        return objectClass;
    }

    public void setObjectClass(String objectClass)
    {
        this.objectClass = objectClass;
    }

    public String getAdvancedQuery()
    {
        return advancedQuery;
    }

    public void setAdvancedQuery(String advancedQuery)
    {
        this.advancedQuery = advancedQuery;
    }

    public String getUserPasswordAttribute()
    {
        return userPasswordAttribute;
    }

    public void setUserPasswordAttribute(String userPasswordAttribute)
    {
        this.userPasswordAttribute = userPasswordAttribute;
    }

    public String getGroupSearchBase()
    {
        return groupSearchBase;
    }

    public void setGroupSearchBase(String groupSearchBase)
    {
        this.groupSearchBase = groupSearchBase;
    }

    public String getGroupObjectClass()
    {
        return groupObjectClass;
    }

    public void setGroupObjectClass(String groupObjectClass)
    {
        this.groupObjectClass = groupObjectClass;
    }

    public String getGroupMemberAttribute()
    {
        return groupMemberAttribute;
    }

    public void setGroupMemberAttribute(String groupMemberAttribute)
    {
        this.groupMemberAttribute = groupMemberAttribute;
    }

    public String getGroupNameAttribute()
    {
        return groupNameAttribute;
    }

    public void setGroupNameAttribute(String groupNameAttribute)
    {
        this.groupNameAttribute = groupNameAttribute;
    }

    public String getGroupAdvancedQuery()
    {
        return groupAdvancedQuery;
    }

    public void setGroupAdvancedQuery(String groupAdvancedQuery)
    {
        this.groupAdvancedQuery = groupAdvancedQuery;
    }

    public boolean isNestedGroupsSupported()
    {
        return nestedGroupsSupported;
    }

    public void setNestedGroupsSupported(boolean nestedGroupsSupported)
    {
        this.nestedGroupsSupported = nestedGroupsSupported;
    }

    public long getGroupReloadInterval()
    {
        return groupReloadInterval;
    }

    public void setGroupReloadInterval(long groupReloadInterval)
    {
        this.groupReloadInterval = groupReloadInterval;
    }

    public String getGroupReloadTimeOfDay()
    {
        return groupReloadTimeOfDay;
    }

    public void setGroupReloadTimeOfDay(String groupReloadTimeOfDay)
    {
        this.groupReloadTimeOfDay = groupReloadTimeOfDay;
    }

    public boolean isWriteEnabled()
    {
        return writeEnabled;
    }

    public void setWriteEnabled(boolean writeEnabled)
    {
        this.writeEnabled = writeEnabled;
    }

    public boolean isCanRegisterUsers()
    {
        return canRegisterUsers;
    }

    public void setCanRegisterUsers(boolean canRegisterUsers)
    {
        this.canRegisterUsers = canRegisterUsers;
    }

    public boolean isCaseInsensitive()
    {
        return caseInsensitive;
    }

    public void setCaseInsensitive(boolean caseInsensitive)
    {
        this.caseInsensitive = caseInsensitive;
    }

    public boolean isExternalAuthentication()
    {
        return externalAuthentication;
    }

    public void setExternalAuthentication(boolean externalAuthentication)
    {
        this.externalAuthentication = externalAuthentication;
    }

    public String getPasswordScheme()
    {
        return passwordScheme;
    }

    public void setPasswordScheme(String passwordScheme)
    {
        this.passwordScheme = passwordScheme;
    }

    public Map<String, String> getAttributeMapping()
    {
        return attributeMapping;
    }

    public void setAttributeMapping(Map<String, String> attributeMapping)
    {
        this.attributeMapping = attributeMapping;
    }

    public Set<String> getBinaryAttributes()
    {
        return binaryAttributes;
    }

    public void setBinaryAttributes(Set<String> binaryAttributes)
    {
        this.binaryAttributes = binaryAttributes;
    }

    public Set<String> getBooleanAttributes()
    {
        return booleanAttributes;
    }

    public void setBooleanAttributes(Set<String> booleanAttributes)
    {
        this.booleanAttributes = booleanAttributes;
    }

    public String getDomain()
    {
        return domain;
    }

    public void setDomain(String domain)
    {
        this.domain = domain;
    }

    public String getDomainAttribute()
    {
        return domainAttribute;
    }

    public void setDomainAttribute(String domainAttribute)
    {
        this.domainAttribute = domainAttribute;
    }
}
