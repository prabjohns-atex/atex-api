package com.atex.desk.api;

import com.atex.desk.api.auth.CognitoProperties;
import com.atex.desk.api.auth.LdapProperties;
import com.atex.desk.api.config.ConfigProperties;
import com.atex.desk.api.config.ImageServiceProperties;
import com.atex.desk.api.plugin.PartitionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.atex.desk.api", "com.atex.onecms"})
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
