package com.familynest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@Order(Ordered.HIGHEST_PRECEDENCE)  // Ensure this config takes priority
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    @Order(-100) // Highest precedence to override default configurations
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.debug("Configuring security filter chain with HIGHEST PRECEDENCE");
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {
                logger.debug("Configuring CORS");
                cors.configurationSource(corsConfigurationSource());
            })
            .sessionManagement(session -> {
                logger.debug("Configuring session management");
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            })
            .authorizeHttpRequests(auth -> {
                logger.debug("Configuring authorization to PERMIT ALL REQUESTS");
                // Configure security for all paths
                auth.requestMatchers("/api/users/login").permitAll()
                    .requestMatchers("/api/users").permitAll()
                    .requestMatchers("/api/users/connection-test").permitAll()
                    .requestMatchers("/api/users/test-token").permitAll()
                    .requestMatchers("/api/users/test-token-101").permitAll()
                    .requestMatchers("/api/users/debug-token").permitAll()
                    .requestMatchers("/api/users/test").permitAll()
                    .requestMatchers("/api/users/101").permitAll()
                    .requestMatchers("/api/users/101/**").permitAll()
                    .requestMatchers("/api/emergency/**").permitAll()  // Allow all emergency endpoints
                    .requestMatchers("/api/videos/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/public/**").permitAll() // Permit public endpoints
                    .requestMatchers("/test/**").permitAll() // Permit test endpoints
                    .requestMatchers("/uploads/**").permitAll() // Permit access to uploaded files
                    .anyRequest().authenticated();
            })
            .headers(headers -> {
                logger.debug("Configuring security headers");
                headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                );
            });

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins during development
        configuration.addAllowedOrigin("*");
        // Explicitly add common emulator origins
        configuration.addAllowedOriginPattern("http://10.0.2.2:[*]");
        configuration.addAllowedOriginPattern("http://10.0.0.2:[*]");
        // Add more mobile app origins
        configuration.addAllowedOriginPattern("http://localhost:[*]");
        configuration.addAllowedOriginPattern("capacitor://localhost");
        configuration.addAllowedOriginPattern("https://*.familynest.app");
        configuration.addAllowedOriginPattern("ionic://*");
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin"));
        configuration.setAllowCredentials(false);  // Must be false when allowedOrigins contains "*"
        configuration.setMaxAge(3600L);
        configuration.setExposedHeaders(Arrays.asList("Content-Type", "Content-Length"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 