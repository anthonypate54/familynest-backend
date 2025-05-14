package com.familynest.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.SecurityFilterChain;

import com.familynest.auth.AuthFilter;

import jakarta.servlet.Filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class SecurityFilterConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityFilterConfig.class);
    
    /**
     * Registers our custom AuthFilter with Spring's filter chain with highest precedence
     * to ensure it runs before Spring Security filters.
     */
    @Bean
    public FilterRegistrationBean<Filter> authFilterRegistration(AuthFilter authFilter) {
        logger.info("⚡ Registering custom AuthFilter with HIGHEST_PRECEDENCE");
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(authFilter);
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Run before all other filters
        registrationBean.setName("authFilter");
        return registrationBean;
    }
} 