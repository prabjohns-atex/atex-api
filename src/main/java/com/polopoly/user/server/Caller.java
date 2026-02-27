package com.polopoly.user.server;

import java.io.Serializable;

/**
 * Aggregates principal (user ID) with credentials.
 */
public class Caller implements Serializable {
    public static final Caller NOBODY_CALLER = new Caller(null);

    private final String loginName;
    private String sessionKey;

    public Caller(String loginName) {
        this.loginName = loginName;
    }

    public String getLoginName() { return loginName; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    @Override
    public String toString() {
        return "Caller{loginName='" + loginName + "'}";
    }
}
