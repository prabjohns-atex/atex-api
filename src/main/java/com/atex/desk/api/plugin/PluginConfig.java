package com.atex.desk.api.plugin;

import org.pf4j.DefaultPluginManager;
import org.pf4j.JarPluginManager;
import org.pf4j.PluginManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@Configuration
@EnableConfigurationProperties(PluginProperties.class)
public class PluginConfig {

    private static final Logger LOG = Logger.getLogger(PluginConfig.class.getName());

    @Bean(destroyMethod = "stopPlugins")
    public PluginManager pluginManager(PluginProperties properties) {
        if (!properties.isEnabled()) {
            LOG.info("Plugin system is disabled");
            return new DefaultPluginManager(Path.of(properties.getDirectory())) {
                @Override
                public void loadPlugins() {
                }

                @Override
                public void startPlugins() {
                }
            };
        }

        Path pluginsDir = Path.of(properties.getDirectory());
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create plugins directory: " + pluginsDir, e);
        }

        JarPluginManager manager = new JarPluginManager(pluginsDir);
        manager.loadPlugins();
        manager.startPlugins();

        LOG.info("Plugin system initialized: " + manager.getPlugins().size() + " plugin(s) loaded from " + pluginsDir.toAbsolutePath());
        return manager;
    }
}
