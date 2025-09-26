package com.familynest.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for authentication filter registration.
 */
@Configuration
public class AuthConfig {
    
    @Autowired
    private AuthFilter authFilter;

    /**
     * Register our custom AuthFilter with Spring Boot
     */
    @Bean
    public FilterRegistrationBean<AuthFilter> filterRegistrationBean() {
        FilterRegistrationBean<AuthFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(authFilter);
        registrationBean.addUrlPatterns("/api/*");
        return registrationBean;
    }
}