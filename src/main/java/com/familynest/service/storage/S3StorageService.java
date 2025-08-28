package com.familynest.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.ObjectMetadata;


import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Implementation of StorageService that stores files in AWS S3
 */
@Service
@Profile({"staging", "production"}) // Active in staging and production environments
public class S3StorageService implements StorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);
    
    @Autowired
    private Environment environment;
    
    @Value("${cloud.aws.credentials.access-key:dummy}")
    private String accessKey;
    
    @Value("${cloud.aws.credentials.secret-key:dummy}")
    private String secretKey;
    
    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;
    
    @Value("${cloud.aws.s3.bucket:familynest-bucket}")
    private String bucketName;
    
    @Value("${storage.url.prefix:}")
    private String urlPrefix;
    
    private AmazonS3 s3Client;
    
    @PostConstruct
    private void initializeAmazon() {
        // Check if we're in a profile that uses S3
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isS3Profile = false;
        for (String profile : activeProfiles) {
            if ("staging".equals(profile) || "production".equals(profile)) {
                isS3Profile = true;
                break;
            }
        }
        
        if (!isS3Profile) {
            logger.info("S3 initialization skipped - not in staging/production profile. Active profiles: {}", 
                String.join(", ", activeProfiles));
            return;
        }
        
        logger.info("Initializing S3 client for profile: {}", String.join(", ", activeProfiles));
        
        try {
            AWSCredentials credentials = new BasicAWSCredentials(this.accessKey, this.secretKey);
            this.s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(region)
                    .build();
            
            logger.info("Initialized Amazon S3 client with bucket: {}", bucketName);
            
            // Check if bucket exists or create it
            if (!s3Client.doesBucketExistV2(bucketName)) {
                s3Client.createBucket(bucketName);
                logger.info("Created S3 bucket: {}", bucketName);
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize S3 client: {}", e.getMessage());
        }
    }
    
    @Override
    public void init() {
        // Nothing to do for S3 initialization as bucket creation is handled in initializeAmazon
        logger.info("S3 storage service initialized for bucket: {}", bucketName);
    }
    
    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        if (s3Client == null) {
            throw new IOException("S3 client not initialized");
        }
        
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }
        
        try {
            // Create S3 object key with directory prefix
            String key = directory + "/" + filename;
            
            // Set object metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            
            // Upload file to S3
            s3Client.putObject(bucketName, key, file.getInputStream(), metadata);
            
            // Note: ACLs are disabled on this bucket - public access is managed through bucket policy
            
            logger.info("Stored file {} in S3 bucket: {}, directory: {}", filename, bucketName, directory);
            
            // Return the relative path
            return "/" + key;
        } catch (Exception e) {
            logger.error("Failed to store file {} in S3", filename, e);
            throw new IOException("Failed to store file in S3", e);
        }
    }
    
    @Override
    public Resource loadAsResource(String path) {
        if (s3Client == null) {
            logger.error("S3 client not initialized");
            return null;
        }
        
        try {
            // Remove leading slash if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            // Check if object exists
            if (!s3Client.doesObjectExist(bucketName, path)) {
                logger.error("File does not exist in S3: {}", path);
                return null;
            }
            
            // Get S3 object URL
            URL url = s3Client.getUrl(bucketName, path);
            
            // Create resource from URL
            return new UrlResource(url);
        } catch (Exception e) {
            // General exception to catch any issues
            logger.error("Could not read file from S3: {}", path, e);
            return null;
        }
    }
    
    @Override
    public boolean exists(String path) {
        if (s3Client == null) {
            return false;
        }
        
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        return s3Client.doesObjectExist(bucketName, path);
    }
    
    @Override
    public boolean delete(String path) {
        if (s3Client == null) {
            return false;
        }
        
        try {
            // Remove leading slash if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            
            s3Client.deleteObject(bucketName, path);
            return true;
        } catch (Exception e) {
            logger.error("Could not delete file from S3: {}", path, e);
            return false;
        }
    }
    
    @Override
    public Path getAbsolutePath(String path) {
        // This doesn't apply for S3, but we need to implement the interface
        // Return a path that indicates it's in S3
        return Paths.get("s3://" + bucketName + "/" + path);
    }
    
    @Override
    public String getUrl(String path) {
        if (s3Client == null) {
            // Return a dummy URL when not in S3 profile
            return "/dummy-s3-path" + path;
        }
        
        // For S3, we return a URL that points to the S3 bucket or CDN
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        
        // Use the configured URL prefix for S3 storage
        if (urlPrefix != null && !urlPrefix.isEmpty()) {
            // Remove trailing slash from prefix if present
            if (urlPrefix.endsWith("/")) {
                return urlPrefix + path;
            } else {
                return urlPrefix + "/" + path;
            }
        }
        
        // Fallback to direct S3 URL
        return s3Client.getUrl(bucketName, path).toString();
    }
    
    /**
     * Downloads a file from S3 to a temporary local file for processing
     * @param s3Path The S3 path to download
     * @return Local file path of the downloaded file, or null if failed
     */
    public String downloadTemporarily(String s3Path) {
        if (s3Client == null) {
            logger.error("S3 client not initialized");
            return null;
        }
        
        try {
            // Remove leading slash if present
            if (s3Path.startsWith("/")) {
                s3Path = s3Path.substring(1);
            }
            
            // Check if object exists
            if (!s3Client.doesObjectExist(bucketName, s3Path)) {
                logger.error("File does not exist in S3: {}", s3Path);
                return null;
            }
            
            // Get S3 object
            S3Object s3Object = s3Client.getObject(bucketName, s3Path);
            
            // Create temporary file
            String fileName = s3Path.substring(s3Path.lastIndexOf('/') + 1);
            String tempDir = System.getProperty("java.io.tmpdir");
            File tempFile = new File(tempDir, "familynest_temp_" + System.currentTimeMillis() + "_" + fileName);
            
            // Download to temporary file
            try (InputStream inputStream = s3Object.getObjectContent();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            logger.info("Downloaded S3 file {} to temporary location: {}", s3Path, tempFile.getAbsolutePath());
            return tempFile.getAbsolutePath();
            
        } catch (Exception e) {
            logger.error("Failed to download file {} from S3 temporarily", s3Path, e);
            return null;
        }
    }
    
    /**
     * Cleans up a temporary file
     * @param tempFilePath Path to the temporary file to delete
     */
    public void cleanupTempFile(String tempFilePath) {
        if (tempFilePath != null) {
            try {
                File tempFile = new File(tempFilePath);
                if (tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    if (deleted) {
                        logger.debug("Cleaned up temporary file: {}", tempFilePath);
                    } else {
                        logger.warn("Failed to delete temporary file: {}", tempFilePath);
                    }
                }
            } catch (Exception e) {
                logger.error("Error cleaning up temporary file: {}", tempFilePath, e);
            }
        }
    }
    
    /**
     * Get the S3 client for direct operations
     * @return AmazonS3 client
     */
    public AmazonS3 getS3Client() {
        return s3Client;
    }
    
    /**
     * Get the bucket name
     * @return S3 bucket name
     */
    public String getBucketName() {
        return bucketName;
    }
} 