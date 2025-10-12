package com.familynest.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Configuration class for Google Play API client
 * This separates the API client initialization from the controller
 * and allows for proper dependency injection and error handling
 */
@Configuration
public class GooglePlayConfig {

    private static final Logger logger = LoggerFactory.getLogger(GooglePlayConfig.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    // Get the path from application properties, with fallback to environment variable
    @Value("${google.play.service-account-key-path:${GOOGLE_APPLICATION_CREDENTIALS:}}")
    private String serviceAccountKeyPath;

    @Bean
    public AndroidPublisher androidPublisher() throws GeneralSecurityException, IOException {
        if (serviceAccountKeyPath == null || serviceAccountKeyPath.isEmpty()) {
            logger.error("❌ Google Play service account key path is not set.");
            throw new IllegalArgumentException("Service account key path is missing. Set it in application.properties or GOOGLE_APPLICATION_CREDENTIALS environment variable.");
        }

        try {
            GoogleCredential credential = GoogleCredential.fromStream(
                new FileInputStream(serviceAccountKeyPath)
            ).createScoped(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER));
            
            AndroidPublisher publisher = new AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential
            ).setApplicationName("FamilyNest").build();
            
            logger.info("✅ Google Play API client initialized successfully");
            return publisher;
        } catch (GeneralSecurityException | IOException e) {
            logger.error("❌ Failed to initialize Google Play API client", e);
            throw new RuntimeException("Failed to initialize Google Play API client", e);
        }
    }
}
