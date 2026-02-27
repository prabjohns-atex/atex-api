package com.atex.onecms.ws.common;
import com.atex.onecms.content.Subject;
public class UserContext {
    private Subject subject;
    private String userId;
    public UserContext() {}
    public UserContext(Subject subject, String userId) { this.subject = subject; this.userId = userId; }
    public Subject getSubject() { return subject; }
    public void setSubject(Subject v) { this.subject = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }
}
