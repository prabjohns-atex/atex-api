package com.atex.desk.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Detailed user response for /principals/users/me, matching reference OneCMS format.
 */
public class UserMeDto {

    private String loginName;
    private String firstName;
    private String lastName;
    private int id;
    private String userId;
    private boolean cmUser;
    private boolean ldapUser;
    private boolean remoteUser;
    private List<GroupRefDto> groups;
    private Map<String, Object> userData;
    private String homeDepartmentId;
    private List<String> workingSites;

    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isCmUser() { return cmUser; }
    public void setCmUser(boolean cmUser) { this.cmUser = cmUser; }

    public boolean isLdapUser() { return ldapUser; }
    public void setLdapUser(boolean ldapUser) { this.ldapUser = ldapUser; }

    public boolean isRemoteUser() { return remoteUser; }
    public void setRemoteUser(boolean remoteUser) { this.remoteUser = remoteUser; }

    public List<GroupRefDto> getGroups() { return groups; }
    public void setGroups(List<GroupRefDto> groups) { this.groups = groups; }

    public Map<String, Object> getUserData() { return userData; }
    public void setUserData(Map<String, Object> userData) { this.userData = userData; }

    public String getHomeDepartmentId() { return homeDepartmentId; }
    public void setHomeDepartmentId(String homeDepartmentId) { this.homeDepartmentId = homeDepartmentId; }

    public List<String> getWorkingSites() { return workingSites; }
    public void setWorkingSites(List<String> workingSites) { this.workingSites = workingSites; }
}
