package com.polopoly.cm.client;

/**
 * User data holder interface.
 */
public interface UserData {
    String ATTRG_USER = "attrg_user";
    String ATTR_LOGIN_NAME = "loginName";

    String getLoginName();
    String getUserId();
    String getFirstName();
    String getSurname();
    String getEmail();
}
