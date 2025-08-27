package com.familynest.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of StorageService that stores files on the local filesystem
 */
@Service
@Profile({"default", "test", "testdb"}) // Active by default and for test profiles
public class LocalStorageService implements StorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);
    
    @Value("${file.upload-dir:/tmp/familynest-uploads}")
    private String uploadDir;
    
    @Value("${storage.url.prefix:}")
    private String urlPrefix;
    
    @PostConstruct
    public void init() {
        try {
            Path rootLocation = Paths.get(uploadDir);
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
                logger.info("Created root upload directory: {}", rootLocation);
            }
            
            // Create standard subdirectories
            createSubdirectory("videos");
            createSubdirectory("thumbnails");
            createSubdirectory("photos");
        } catch (IOException e) {
            logger.error("Could not initialize storage", e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }
    
    private void createSubdirectory(String name) throws IOException {
        Path dir = Paths.get(uploadDir, name);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            logger.info("Created subdirectory: {}", dir);
        }
    }
    
    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }
        
        Path dirPath = Paths.get(uploadDir, directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        
        Path destinationFile = dirPath.resolve(filename).normalize();
        
        // Security check - make sure the resolved file is still within our upload directory
        if (!destinationFile.getParent().equals(dirPath)) {
            throw new IOException("Cannot store file outside current directory");
        }
        
        try {
            Files.write(destinationFile, file.getBytes());
            logger.info("Stored file {} in directory {}", filename, directory);
            
            // Return the relative path that can be used for retrieval
            return "/" + directory + "/" + filename;
        } catch (IOException e) {
            logger.error("Failed to store file {}", filename, e);
            throw e;
        }
    }
    
    @Override
    public Resource loadAsResource(String path) {
        try {
            // Remove leading slash if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            Path file = Paths.get(uploadDir).resolve(path).normalize();
            logger.info("Attempting to load resource from path: {}", file.toAbsolutePath());
            Resource resource = new UrlResource(file.toUri());
            
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                logger.error("Could not read file: {}", path);
                return null;
            }
        } catch (MalformedURLException e) {
            logger.error("Could not read file: {}", path, e);
            return null;
        }
    }
    
    @Override
    public boolean exists(String path) {
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        Path file = Paths.get(uploadDir).resolve(path).normalize();
        return Files.exists(file);
    }
    
    @Override
    public boolean delete(String path) {
        try {
            // Remove leading slash if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            Path file = Paths.get(uploadDir).resolve(path).normalize();
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.error("Could not delete file: {}", path, e);
            return false;
        }
    }
    
    @Override
    public Path getAbsolutePath(String path) {
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        return Paths.get(uploadDir).resolve(path).normalize();
    }
    
    @Override
    public String getUrl(String path) {
        // For local storage with relative paths, just return the path with /uploads prefix
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        // Apply URL prefix if configured (empty by default for local storage)
        if (urlPrefix != null && !urlPrefix.isEmpty()) {
            // Remove trailing slash from prefix if present
            if (urlPrefix.endsWith("/") && path.startsWith("/")) {
                return urlPrefix + path.substring(1);
            } else {
                return urlPrefix + path;
            }
        }
        
        // Default to /uploads prefix for local files
        return "/uploads" + path;
    }
} 