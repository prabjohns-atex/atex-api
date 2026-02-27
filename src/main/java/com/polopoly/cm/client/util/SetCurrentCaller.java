package com.polopoly.cm.client.util;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.user.server.Caller;
public class SetCurrentCaller implements AutoCloseable {
    private final PolicyCMServer server;
    private final Caller previousCaller;
    public SetCurrentCaller(PolicyCMServer server, Caller caller) {
        this.server = server;
        this.previousCaller = server.getCurrentCaller();
        server.setCurrentCaller(caller);
    }
    @Override
    public void close() {
        server.setCurrentCaller(previousCaller);
    }
}
