package com.familynest.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);
    
    @PostConstruct
    public void initializeFirebase() {
        try {
            // Check if Firebase is already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                // Try to load service account key from classpath
                try {
                    InputStream serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();
                    
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .setProjectId("familynest-notifications")
                            .build();
                    
                    FirebaseApp.initializeApp(options);
                    logger.info("✅ Firebase Admin SDK initialized successfully");
                } catch (IOException e) {
                    logger.warn("⚠️ firebase-service-account.json not found in classpath, trying default credentials");
                    
                    // Fallback to default credentials (for production with environment variables)
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.getApplicationDefault())
                            .setProjectId("familynest-notifications")
                            .build();
                    
                    FirebaseApp.initializeApp(options);
                    logger.info("✅ Firebase Admin SDK initialized with default credentials");
                }
            } else {
                logger.info("🔥 Firebase Admin SDK already initialized");
            }
        } catch (Exception e) {
            logger.error("❌ Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
            // Don't throw exception - let the app start without Firebase if needed
            logger.warn("⚠️ Push notifications will be disabled due to Firebase initialization failure");
        }
    }
} 
