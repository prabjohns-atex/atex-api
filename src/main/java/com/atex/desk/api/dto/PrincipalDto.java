package com.atex.desk.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * User principal info for the /principals/users list endpoint,
 * matching reference OneCMS format.
 */
@Schema(description = "User principal info")
public class PrincipalDto
{
    @Schema(description = "Principal type", example = "user")
    private String type = "user";

    @Schema(description = "Numeric user ID")
    private String id;

    @Schema(description = "User login name")
    private String name;

    @Schema(description = "Principal ID (same as id)")
    private String principalId;

    @Schema(description = "True if this is a CMS user (not LDAP, not remote)")
    private boolean cmUser;

    @Schema(description = "True if this is an LDAP user")
    private boolean ldapUser;

    @Schema(description = "True if this is a remote user")
    private boolean remoteUser;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }

    public boolean isCmUser() { return cmUser; }
    public void setCmUser(boolean cmUser) { this.cmUser = cmUser; }

    public boolean isLdapUser() { return ldapUser; }
    public void setLdapUser(boolean ldapUser) { this.ldapUser = ldapUser; }

    public boolean isRemoteUser() { return remoteUser; }
    public void setRemoteUser(boolean remoteUser) { this.remoteUser = remoteUser; }
}
