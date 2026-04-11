package com.atex.desk.api;

import com.atex.desk.api.auth.CognitoProperties;
import com.atex.desk.api.auth.LdapProperties;
import com.atex.desk.api.config.ConfigProperties;
import com.atex.desk.api.config.ImageServiceProperties;
import com.atex.desk.api.plugin.PartitionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.atex.desk.api",
    "com.atex.onecms",
    "com.atex.plugins",         // built-in optional plugins (layout, copyfit)
    "com.atex.customer"          // customer classpath extensions (allied, mpp, etc.)
})
@EntityScan(basePackages = {
    "com.atex.desk.api.entity",
    "com.atex.customer"          // allow customer plugins to add JPA entities
})
@EnableJpaRepositories(basePackages = {
    "com.atex.desk.api.repository",
    "com.atex.customer"          // allow customer plugins to add repositories
})
@EnableConfigurationProperties({PartitionProperties.class, ConfigProperties.class,
                                LdapProperties.class, CognitoProperties.class,
                                ImageServiceProperties.class})
public class DeskApiApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(DeskApiApplication.class, args);
    }

}
