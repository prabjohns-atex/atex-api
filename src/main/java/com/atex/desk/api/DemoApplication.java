package com.atex.desk.api;

import com.atex.desk.api.config.ConfigProperties;
import com.atex.desk.api.plugin.PartitionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PartitionProperties.class, ConfigProperties.class})
public class DemoApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(DemoApplication.class, args);
    }

}
