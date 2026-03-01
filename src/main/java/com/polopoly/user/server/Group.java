package com.polopoly.user.server;

import java.util.Collections;
import java.util.Set;

/**
 * Group interface — Polopoly group management.
 */
public interface Group {

    GroupId getGroupId();

    String getName();

    boolean isMember(String userId) throws Exception;

    default Set<String> getMembers() {
        return Collections.emptySet();
    }

    default void addMember(String principalId) throws Exception {
        throw new UnsupportedOperationException("addMember not implemented");
    }

    default void removeMember(String principalId) throws Exception {
        throw new UnsupportedOperationException("removeMember not implemented");
    }
}
