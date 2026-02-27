package com.polopoly.cm.client;

import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.user.server.UserServer;

/**
 * Client interface providing access to CM services.
 */
public interface CmClient {

    PolicyCMServer getPolicyCMServer();

    UserServer getUserServer();
}
