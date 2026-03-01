package com.atex.desk.api.auth;

import com.atex.desk.api.config.DeskProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TokenProperties.class, DeskProperties.class})
public class AuthConfig
{
    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(TokenService tokenService,
                                                                      TokenProperties properties)
    {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthFilter(tokenService, properties.isEnabled()));
        registration.addUrlPatterns("/content/*", "/dam/*", "/principals/*", "/admin/*", "/search/*");
        registration.setOrder(1);
        return registration;
    }
}
