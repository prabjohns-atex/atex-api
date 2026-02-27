package com.polopoly.user.server;

/**
 * Group interface stub.
 */
public interface Group {

    GroupId getGroupId();

    String getName();

    boolean isMember(String userId) throws Exception;
}
