package com.atex.desk.api.onecms;

import com.polopoly.cm.client.CmClient;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.user.server.UserServer;
import org.springframework.stereotype.Component;

/**
 * Local CmClient implementation. Provides access to LocalPolicyCMServer
 * and a stub UserServer.
 */
@Component
public class LocalCmClient implements CmClient {

    private final PolicyCMServer policyCMServer;
    private final UserServer userServer;

    public LocalCmClient(PolicyCMServer policyCMServer, UserServer userServer) {
        this.policyCMServer = policyCMServer;
        this.userServer = userServer;
    }

    @Override
    public PolicyCMServer getPolicyCMServer() {
        return policyCMServer;
    }

    @Override
    public UserServer getUserServer() {
        return userServer;
    }
}
