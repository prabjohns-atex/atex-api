package com.polopoly.user.server;

/**
 * User server interface stub.
 */
public interface UserServer {

    Caller loginAndMerge(String loginName, String password, Caller caller) throws Exception;

    String getUserIdByLoginName(String loginName) throws Exception;

    Group findGroup(GroupId groupId) throws Exception;
}
