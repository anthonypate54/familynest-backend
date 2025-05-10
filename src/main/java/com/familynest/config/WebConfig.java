package com.familynest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

/**
 * Web configuration for the application
 * Ensures proper scanning of controllers and component initialization
 */
@Configuration
@EnableWebMvc
@EnableTransactionManagement
@EnableAspectJAutoProxy
public class WebConfig implements WebMvcConfigurer {
    
    /**
     * Configure JdbcTemplate for direct SQL queries
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
} 