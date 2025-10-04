package com.familynest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration to prevent database connection pool timeouts after idle periods.
 * This class schedules a periodic ping to the database to keep connections alive.
 */
@Configuration
@EnableScheduling
public class DatabaseKeepAliveConfig {
    
    private final JdbcTemplate jdbcTemplate;
    private static final Logger logger = LoggerFactory.getLogger(DatabaseKeepAliveConfig.class);
    
    public DatabaseKeepAliveConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        logger.info("Database keep-alive scheduler initialized");
    }
    
    /**
     * Executes a simple query every 5 minutes to keep the connection pool fresh.
     * This prevents timeouts when the server has been idle for extended periods.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void pingDatabase() {
        try {
            logger.debug("Executing keep-alive ping to database");
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                logger.debug("Database keep-alive ping successful");
            } else {
                logger.warn("Database keep-alive ping returned unexpected result: {}", result);
            }
        } catch (Exception e) {
            logger.error("Database keep-alive ping failed", e);
        }
    }
}
