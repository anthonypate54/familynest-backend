package com.familynest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.beans.factory.annotation.Value;
import com.familynest.auth.AuthFilter;
import com.familynest.auth.AuthUtil;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import com.familynest.controller.VideoController;
import com.familynest.controller.TestVideoController;
import org.mockito.Mockito;
import com.familynest.service.ThumbnailService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for the application.
 * This class:
 * 1. Disables Liquibase for tests
 * 2. Configures security to allow all requests
 * 3. Sets up test authentication
 */
@Configuration
@Profile("test")
@EnableWebSecurity
@EnableAutoConfiguration(exclude = {LiquibaseAutoConfiguration.class})
@ComponentScan(basePackages = "com.familynest", 
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, 
        classes = AuthFilter.class))
public class TestConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
            .anyRequest().permitAll(); // Disable authentication for all requests during testing
        System.out.println("DEBUG: Security disabled for test environment - all requests permitted");
        return http.build();
    }

    @Bean
    public TestAuthFilter testAuthFilter() {
        return new TestAuthFilter();
    }

    @Bean
    public FilterRegistrationBean<TestAuthFilter> testAuthFilterRegistration(TestAuthFilter filter) {
        FilterRegistrationBean<TestAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> disableAuthFilter() {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setEnabled(false);
        return registration;
    }
    
    @Bean
    @Primary
    public AuthUtil mockAuthUtil() {
        AuthUtil mock = Mockito.mock(AuthUtil.class);
        Mockito.when(mock.validateToken(Mockito.anyString())).thenReturn(true);
        Mockito.when(mock.extractUserId(Mockito.anyString())).thenReturn(1L); // Default test user ID
        Mockito.when(mock.getUserRole(Mockito.anyString())).thenReturn("USER");
        return mock;
    }
    
    @Bean(name = "videoController")
    public VideoController videoController() {
        return new TestVideoController();
    }
    
    @Bean
    public ThumbnailService thumbnailService() {
        return Mockito.mock(ThumbnailService.class);
    }
    
    @Bean
    public JdbcTemplate jdbcTemplate() {
        return Mockito.mock(JdbcTemplate.class);
    }
} 