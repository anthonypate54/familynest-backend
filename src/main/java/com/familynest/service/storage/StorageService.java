package com.familynest.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for different storage service implementations (local, S3, etc.)
 */
public interface StorageService {
    
    /**
     * Initialize the storage
     */
    void init();
    
    /**
     * Store a file in the storage
     * @param file The file to store
     * @param directory The directory to store the file in
     * @param filename The filename to use
     * @return The relative path to the stored file
     * @throws IOException if storage fails
     */
    String store(MultipartFile file, String directory, String filename) throws IOException;
    
    /**
     * Get a file from storage as a resource
     * @param path The relative path to the file
     * @return The file as a resource
     */
    Resource loadAsResource(String path);
    
    /**
     * Check if a file exists
     * @param path The relative path to the file
     * @return true if the file exists, false otherwise
     */
    boolean exists(String path);
    
    /**
     * Delete a file
     * @param path The relative path to the file
     * @return true if the deletion was successful, false otherwise
     */
    boolean delete(String path);
    
    /**
     * Get the absolute storage location for a given path
     * @param path The relative path
     * @return The absolute path in the storage system
     */
    Path getAbsolutePath(String path);
    
    /**
     * Get the URL for a file
     * @param path The relative path to the file
     * @return The URL to access the file
     */
    String getUrl(String path);
} 