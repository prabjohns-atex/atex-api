package com.atex.desk.api.auth;

import com.atex.desk.api.config.DeskProperties;
import com.atex.desk.api.config.RequestMetricsFilter;
import com.atex.desk.api.service.RequestMetricsService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TokenProperties.class, DeskProperties.class})
public class AuthConfig
{
    @Bean
    public FilterRegistrationBean<RequestMetricsFilter> requestMetricsFilterRegistration(
            RequestMetricsService metricsService) {
        FilterRegistrationBean<RequestMetricsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestMetricsFilter(metricsService));
        registration.addUrlPatterns("/*");
        registration.setOrder(-1); // Before auth filter
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(TokenService tokenService,
                                                                      TokenProperties properties)
    {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthFilter(tokenService, properties.isEnabled()));
        registration.addUrlPatterns("/content/*", "/dam/*", "/principals/*", "/admin/*", "/search/*", "/changes/*", "/layout/*", "/file/*", "/activities/*", "/preview/*", "/configuration/*", "/image/*", "/metadata/*", "/view/*");
        registration.setOrder(1);
        return registration;
    }

    /**
     * Non-rejecting token decoder that runs on ALL paths, including plugin
     * endpoints. Decodes tokens into request attributes without enforcing auth.
     * Handlers that need auth call {@code DamUserContext.assertLoggedIn()}.
     */
    @Bean
    public FilterRegistrationBean<TokenDecodeFilter> tokenDecodeFilterRegistration(
            TokenService tokenService, TokenProperties properties)
    {
        FilterRegistrationBean<TokenDecodeFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TokenDecodeFilter(tokenService, properties.isEnabled()));
        registration.addUrlPatterns("/*");
        registration.setOrder(2); // After AuthFilter — but AuthFilter only covers legacy paths
        return registration;
    }
}
