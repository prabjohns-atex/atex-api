package com.atex.desk.api.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
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

    /**
     * Configure Tomcat to decode %2F (encoded forward slash) in URL paths.
     * The OneCMS content API uses "externalid/X" as a content ID format,
     * which clients encode as "externalid%2FX". Without this, Tomcat passes
     * %2F through unmodified and Spring path variables remain encoded.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer()
    {
        return factory -> factory.addConnectorCustomizers(connector ->
            connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue()));
    }


}
