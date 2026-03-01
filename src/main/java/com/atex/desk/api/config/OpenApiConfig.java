package com.atex.desk.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig
{
    @Bean
    public OpenAPI deskApiOpenAPI()
    {
        return new OpenAPI()
            .info(new Info()
                .title("Desk API")
                .description("OneCMS-compatible REST layer for the Atex editorial platform")
                .version("0.0.1-SNAPSHOT"))
            .servers(List.of(new Server().url("http://localhost:8081")))
            .components(new Components()
                .addSecuritySchemes("authToken", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-Auth-Token")
                    .description("JWT authentication token")))
            .addSecurityItem(new SecurityRequirement().addList("authToken"))
            .tags(List.of(
                new Tag().name("Authentication").description("Acquire, validate and manage authentication tokens"),
                new Tag().name("Content").description("Create, get, update and delete content"),
                new Tag().name("Workspace").description("Create, get, update and delete content in workspaces"),
                new Tag().name("Principals").description("User and group management"),
                new Tag().name("Configuration").description("Resource-based configuration admin"),
                new Tag().name("Dashboard").description("System status and endpoint listing"),
                new Tag().name("DAM").description("DAM content operations, search, publishing and configuration"),
                new Tag().name("Search").description("Solr search proxy with format negotiation and permission filtering"),
                new Tag().name("Changes").description("Content change feed for polling content lifecycle events")
            ));
    }
}
