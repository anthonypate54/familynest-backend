package com.familynest.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AOP aspect to monitor and log SQL queries
 * This helps identify N+1 query problems and track query efficiency
 */
@Aspect
@Component
public class SqlStatementMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlStatementMonitor.class);
    private final AtomicInteger queryCounter = new AtomicInteger(0);
    
    /**
     * Intercept JdbcTemplate query executions to count and log them
     */
    @Around("execution(* org.springframework.jdbc.core.JdbcTemplate.query*(..))")
    public Object monitorJdbcQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        int currentCount = queryCounter.incrementAndGet();
        Object[] args = joinPoint.getArgs();
        String sql = args.length > 0 && args[0] instanceof String ? (String) args[0] : "Unknown SQL";
        
        logger.info("SQL QUERY #{}: {}", currentCount, formatSql(sql));
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;
        
        logger.info("SQL QUERY #{} completed in {}ms", currentCount, executionTime);
        return result;
    }
    
    /**
     * Intercept JdbcTemplate update executions to count and log them
     */
    @Around("execution(* org.springframework.jdbc.core.JdbcTemplate.update*(..))")
    public Object monitorJdbcUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        int currentCount = queryCounter.incrementAndGet();
        Object[] args = joinPoint.getArgs();
        String sql = args.length > 0 && args[0] instanceof String ? (String) args[0] : "Unknown SQL";
        
        logger.info("SQL UPDATE #{}: {}", currentCount, formatSql(sql));
        
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;
        
        logger.info("SQL UPDATE #{} completed in {}ms", currentCount, executionTime);
        return result;
    }
    
    /**
     * Format SQL for better readability in logs
     */
    private String formatSql(String sql) {
        // Simple formatting - can be enhanced for better readability
        return sql.replaceAll("\\s+", " ").trim();
    }
} 
