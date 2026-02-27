package com.atex.desk.api.onecms;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped caller context. Replaces the Polopoly SetCurrentCaller pattern.
 */
@Component
@RequestScope
public class CallerContext {
    private String principalId;

    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
}
