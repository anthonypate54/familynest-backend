package com.familynest.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of StorageService that stores files in AWS S3
 * This is a stub implementation for now until AWS setup is complete
 */
@Service
@Profile({"production"}) // Only active in production
public class S3StorageService implements StorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);
    
    @Override
    public void init() {
        logger.info("S3 storage service initialized as a stub");
    }
    
    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        logger.info("Stub S3 storage - pretending to store file: {}/{}", directory, filename);
        return "/stubs3/" + directory + "/" + filename;
    }
    
    @Override
    public Resource loadAsResource(String path) {
        logger.info("Stub S3 storage - pretending to load: {}", path);
        return null;
    }
    
    @Override
    public boolean exists(String path) {
        logger.info("Stub S3 storage - pretending to check if exists: {}", path);
        return false;
    }
    
    @Override
    public boolean delete(String path) {
        logger.info("Stub S3 storage - pretending to delete: {}", path);
        return true;
    }
    
    @Override
    public Path getAbsolutePath(String path) {
        logger.info("Stub S3 storage - pretending to get absolute path: {}", path);
        return Paths.get("stub-s3-path");
    }
    
    @Override
    public String getUrl(String path) {
        logger.info("Stub S3 storage - pretending to get URL: {}", path);
        return "/stub-s3" + (path.startsWith("/") ? path : "/" + path);
    }
} 