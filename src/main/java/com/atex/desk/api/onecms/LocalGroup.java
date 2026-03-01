package com.atex.desk.api.onecms;

import com.polopoly.user.server.Group;
import com.polopoly.user.server.GroupId;

import java.util.Collections;
import java.util.Set;

/**
 * Local Group implementation backed by database queries.
 */
public class LocalGroup implements Group {

    private final GroupId groupId;
    private final String name;
    private final Set<String> memberPrincipalIds;

    public LocalGroup(GroupId groupId, String name, Set<String> memberPrincipalIds) {
        this.groupId = groupId;
        this.name = name;
        this.memberPrincipalIds = memberPrincipalIds;
    }

    @Override
    public GroupId getGroupId() {
        return groupId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isMember(String principalId) {
        return memberPrincipalIds.contains(principalId);
    }

    @Override
    public Set<String> getMembers() {
        return Collections.unmodifiableSet(memberPrincipalIds);
    }
}
