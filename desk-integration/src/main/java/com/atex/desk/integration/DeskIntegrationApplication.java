package com.atex.desk.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Desk Integration — content ingest, distribution, and scheduled processing.
 * Runs as a separate container alongside desk-api, sharing the same MySQL and Solr.
 * Depends on desk-api classes (ContentManager, beans, hooks) as a library.
 */
@SpringBootApplication(scanBasePackages = {
    "com.atex.desk.integration",
    "com.atex.desk.api.onecms",
    "com.atex.desk.api.service",
    "com.atex.desk.api.repository",
    "com.atex.desk.api.entity",
    "com.atex.desk.api.auth",
    "com.atex.desk.api.file",
    "com.atex.desk.api.plugin",
    "com.atex.desk.api.indexing"
})
@EnableScheduling
public class DeskIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeskIntegrationApplication.class, args);
    }
}
