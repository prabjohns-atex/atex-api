package com.atex.onecms.app.dam.publish.config;

import java.util.Collections;
import java.util.List;

public class RemoteConfigRuleBean {
    private String id;
    private List<String> users;
    private List<String> groups;
    private List<String> securityParentIds;

    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public List<String> getUsers() { return users != null ? users : Collections.emptyList(); }
    public void setUsers(List<String> v) { this.users = v; }
    public List<String> getGroups() { return groups != null ? groups : Collections.emptyList(); }
    public void setGroups(List<String> v) { this.groups = v; }
    public List<String> getSecurityParentIds() { return securityParentIds != null ? securityParentIds : Collections.emptyList(); }
    public void setSecurityParentIds(List<String> v) { this.securityParentIds = v; }
}
