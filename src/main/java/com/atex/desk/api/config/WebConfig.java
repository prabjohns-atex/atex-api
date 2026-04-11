package com.atex.desk.api.config;

import com.atex.desk.api.auth.DamUserContextArgumentResolver;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

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
     * Register the DamUserContext argument resolver so controller methods
     * can receive an injected user context parameter (Polopoly @AuthUser pattern).
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers)
    {
        resolvers.add(new DamUserContextArgumentResolver());
    }

    /**
     * Strip /onecms prefix from incoming requests.
     * The app was previously deployed as onecms.war, so some configuration
     * still references the /onecms context path. This filter quietly forwards
     * those requests to the root path.
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> onecmsContextFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            jakarta.servlet.FilterChain chain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                String path = request.getRequestURI();
                if (path.startsWith("/onecms/")) {
                    String newPath = path.substring("/onecms".length());
                    request.getRequestDispatcher(newPath).forward(request, response);
                } else if (path.equals("/onecms")) {
                    request.getRequestDispatcher("/").forward(request, response);
                } else {
                    chain.doFilter(request, response);
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/onecms/*", "/onecms");
        reg.setOrder(0);
        return reg;
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
            connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue())
        );
    }


}
