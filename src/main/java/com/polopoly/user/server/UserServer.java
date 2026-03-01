package com.polopoly.user.server;

/**
 * User server interface — Polopoly user management.
 */
public interface UserServer {

    Caller loginAndMerge(String loginName, String password, Caller caller) throws Exception;

    String getUserIdByLoginName(String loginName) throws Exception;

    Group findGroup(GroupId groupId) throws Exception;

    GroupId[] getAllGroups() throws Exception;

    default GroupId[] findGroupsByMember(String principalId) throws Exception {
        throw new UnsupportedOperationException("findGroupsByMember not implemented");
    }

    default boolean isConfiguredWithLdap() {
        return false;
    }

    default boolean isConfiguredWithRemote() {
        return false;
    }

    default String getRemoteOAuthUrl(String callbackUrl) {
        return null;
    }
}
