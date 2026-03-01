package com.atex.onecms.app.dam.publish;

import com.atex.onecms.app.dam.publish.config.RemoteConfigBean;
import com.atex.onecms.app.dam.publish.config.RemoteConfigRuleBean;
import com.atex.onecms.app.dam.publish.config.RemotesConfiguration;
import com.atex.onecms.app.dam.publish.config.RemotesConfigurationFactory;
import com.atex.onecms.content.ContentId;
import com.atex.onecms.content.ContentManager;
import com.atex.onecms.content.Subject;
import com.polopoly.user.server.Caller;
import com.polopoly.user.server.Group;
import com.polopoly.user.server.GroupId;
import com.polopoly.user.server.UserServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DamPublisherFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DamPublisherFactory.class);
    private static final Subject SYSTEM_SUBJECT = new Subject("98", null);

    private final ContentManager contentManager;
    private final UserServer userServer;

    public DamPublisherFactory(ContentManager contentManager, @Nullable UserServer userServer) {
        this.contentManager = contentManager;
        this.userServer = userServer;
    }

    public DamPublisher create(ContentId contentId, Caller caller) {
        PublishingContext ctx = createContext(contentId, caller);
        return create(ctx);
    }

    public DamPublisher create(String backendId, Caller caller) {
        PublishingContext ctx = createContext(backendId, caller);
        return create(ctx);
    }

    public DamPublisher create(PublishingContext ctx) {
        return new DamPublisherBuilder()
            .contentManager(contentManager)
            .context(ctx)
            .build();
    }

    public PublishingContext createContext(ContentId contentId, Caller caller) {
        RemotesConfiguration remotesConfig = getRemotesConfiguration();
        RemoteConfigBean remoteConfig = getRemoteConfig(remotesConfig, contentId, caller);
        DamPublisherConfiguration publishConfig = getPublishConfig(remotesConfig, remoteConfig);
        return new PublishingContextImpl(remoteConfig, publishConfig, contentId, null, caller);
    }

    public PublishingContext createContext(String backendId, Caller caller) {
        RemotesConfiguration remotesConfig = getRemotesConfiguration();
        RemoteConfigBean remoteConfig = remotesConfig.getConfigurations().get(backendId);
        if (remoteConfig == null) {
            // Fall back to default
            String defaultId = remotesConfig.getDefaultRemoteConfigId();
            if (defaultId != null) {
                remoteConfig = remotesConfig.getConfigurations().get(defaultId);
            }
        }
        DamPublisherConfiguration publishConfig = getPublishConfig(remotesConfig, remoteConfig);
        return new PublishingContextImpl(remoteConfig, publishConfig, null, null, caller);
    }

    public String getBackendIdFromApiDomain(String apiDomain) {
        if (apiDomain == null || apiDomain.isEmpty()) return null;
        RemotesConfiguration config = getRemotesConfiguration();
        for (Map.Entry<String, RemoteConfigBean> entry : config.getConfigurations().entrySet()) {
            String url = entry.getValue().getRemoteApiUrl();
            if (url != null && url.contains(apiDomain)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private RemotesConfiguration getRemotesConfiguration() {
        return RemotesConfigurationFactory.fetch(contentManager, SYSTEM_SUBJECT);
    }

    private RemoteConfigBean getRemoteConfig(RemotesConfiguration config, ContentId contentId, Caller caller) {
        // Try to match a rule
        for (RemoteConfigRuleBean rule : config.getPublishRules()) {
            if (matchesRule(rule, caller)) {
                RemoteConfigBean bean = config.getConfigurations().get(rule.getId());
                if (bean != null) return bean;
            }
        }
        // Fall back to default
        String defaultId = config.getDefaultRemoteConfigId();
        if (defaultId != null) {
            RemoteConfigBean bean = config.getConfigurations().get(defaultId);
            if (bean != null) return bean;
        }
        // Return first config if available
        if (!config.getConfigurations().isEmpty()) {
            return config.getConfigurations().values().iterator().next();
        }
        return null;
    }

    private boolean matchesRule(RemoteConfigRuleBean rule, Caller caller) {
        if (caller == null) return false;
        String loginName = caller.getLoginName();
        if (loginName == null) return false;

        // Match by user
        if (!rule.getUsers().isEmpty()) {
            if (rule.getUsers().contains(loginName)) {
                return true;
            }
        }

        // Match by group — use findGroupsByMember for efficiency
        if (!rule.getGroups().isEmpty() && userServer != null) {
            try {
                GroupId[] memberGroups = userServer.findGroupsByMember(loginName);
                if (memberGroups != null) {
                    for (GroupId gid : memberGroups) {
                        Group group = userServer.findGroup(gid);
                        if (group != null && rule.getGroups().contains(group.getName())) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error checking group membership for rule matching: {}", e.getMessage());
            }
        }

        return false;
    }

    private DamPublisherConfiguration getPublishConfig(RemotesConfiguration remotesConfig,
                                                        RemoteConfigBean remoteConfig) {
        String configId = null;
        if (remoteConfig != null) {
            configId = remoteConfig.getPublishConfigId();
        }
        if (configId == null) {
            configId = remotesConfig.getDefaultPublishConfigId();
        }
        if (configId == null) {
            configId = DamPublisherConfiguration.BEAN_PUBLISHER_CONFIG_EXTID;
        }
        return DamPublisherConfiguration.fetch(contentManager, configId, SYSTEM_SUBJECT);
    }
}
