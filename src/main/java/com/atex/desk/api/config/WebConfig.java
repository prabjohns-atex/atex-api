package com.atex.desk.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for dashboard and Swagger UI redirects.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer
{
    @Override
    public void addViewControllers(ViewControllerRegistry registry)
    {
        registry.addRedirectViewController("/dashboard", "/dashboard.html");
        registry.addRedirectViewController("/swagger-ui", "/swagger-ui.html");
    }
}
