package com.familynest.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configuration for web filters.
 */
@Configuration
public class WebFilterConfig {

    /**
     * Register the HTTP method filter with Spring Boot.
     * This ensures it runs before any other filters.
     */
    @Bean
    public FilterRegistrationBean<HttpMethodFilter> httpMethodFilterRegistration(HttpMethodFilter httpMethodFilter) {
        FilterRegistrationBean<HttpMethodFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(httpMethodFilter);
        registration.addUrlPatterns("/*");
        registration.setName("httpMethodFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}





