package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;

public class ModulePublisher {

    private final RemoteConfigBean config;
    private final UserTokenStorage tokenStorage;

    public ModulePublisher() {
        this.config = null;
        this.tokenStorage = new UserTokenStorage();
    }

    public ModulePublisher(RemoteConfigBean config) {
        this.config = config;
        this.tokenStorage = new UserTokenStorage();
    }

    public ContentPublisher createContentPublisher(String username) {
        if (config == null) {
            throw new ContentPublisherException("No remote configuration available");
        }
        return new ContentAPIPublisher(config, username, tokenStorage);
    }
}
