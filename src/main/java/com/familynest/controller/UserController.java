package com.familynest.controller;

import com.familynest.model.Message;
import com.familynest.model.User;
import com.familynest.model.Family;
import com.familynest.repository.MessageRepository;
import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import com.familynest.auth.AuthUtil; // Add this import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familynest.dto.UserDataDTO;
import org.springframework.web.bind.annotation.RequestHeader;
import com.familynest.auth.JwtUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Enumeration;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private AuthUtil authUtil; // Add this dependency

    @Autowired
    private JwtUtil jwtUtil; // Ensure JwtUtil is injected

    @Value("${file.upload-dir:/tmp/familynest-uploads}")
    private String uploadDir;

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint(HttpServletRequest request) {
        logger.debug("Received test request from: {}", request.getRemoteAddr());
        logger.debug("Request URL: {}", request.getRequestURL());
        logger.debug("Request URI: {}", request.getRequestURI());
        logger.debug("Request method: {}", request.getMethod());
        logger.debug("Request headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logger.debug("  {}: {}", headerName, request.getHeader(headerName));
        }
        return ResponseEntity.ok("Test successful");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        logger.debug("Received request for user ID: {}", id);
        logger.debug("Querying user with ID: {}", id);
        User user = userRepository.findById(id).orElse(null);
        logger.debug("Finished querying user with ID: {}", id);
        if (user == null) {
            logger.debug("User not found for ID: {}", id);
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        logger.debug("Found user with username: {}, firstName: {}, lastName: {}, password: {}, role: {}, photo: {}, familyId: {}", 
                    user.getUsername(), user.getFirstName(), user.getLastName(), user.getPassword(), user.getRole(), user.getPhoto(), user.getFamilyId());
        Map<String, Object> response = new HashMap<>();
        response.put("username", user.getUsername());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("password", user.getPassword());
        response.put("role", user.getRole());
        response.put("photo", user.getPhoto());
        response.put("familyId", user.getFamilyId());
        logger.debug("Returning response: {}", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createUser(
        @RequestPart("userData") String userDataJson,
        @RequestPart(value = "photo", required = false) MultipartFile photo) {
        logger.debug("Received request to create user with userData: {}", userDataJson);
        try {
            ObjectMapper mapper = new ObjectMapper();
            UserDataDTO userData = mapper.readValue(userDataJson, UserDataDTO.class);

            // Validate required fields
            if (userData.getUsername() == null || userData.getUsername().length() < 3) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username must be at least 3 characters long"));
            }
            if (userData.getEmail() == null || !userData.getEmail().contains("@")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Valid email is required"));
            }
            if (userData.getPassword() == null || userData.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters long"));
            }
            if (userData.getFirstName() == null || userData.getFirstName().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "First name is required"));
            }
            if (userData.getLastName() == null || userData.getLastName().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Last name is required"));
            }

            // Check if username or email already exists
            if (userRepository.findByUsername(userData.getUsername()) != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
            }
            if (userRepository.findByEmail(userData.getEmail()) != null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
            }

            User user = new User();
            user.setUsername(userData.getUsername());
            user.setEmail(userData.getEmail());
            user.setPassword(authUtil.hashPassword(userData.getPassword()));
            user.setFirstName(userData.getFirstName());
            user.setLastName(userData.getLastName());
            user.setRole(userData.getRole() != null ? userData.getRole() : "USER");
            user.setFamilyId(null);

            if (photo != null && !photo.isEmpty()) {
                String fileName = System.currentTimeMillis() + "_" + photo.getOriginalFilename();
                Path filePath = Paths.get(uploadDir, fileName);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, photo.getBytes());
                user.setPhoto("/api/users/photos/" + fileName);
            }

            User savedUser = userRepository.save(user);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", savedUser.getId());
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Error creating user: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody Map<String, String> loginData) {
        logger.debug("Received login request for email: {}", loginData.get("email"));
        try {
            String email = loginData.get("email");
            String password = loginData.get("password");
            
            if (email == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
            }

            User user = userRepository.findByEmail(email);
            if (user == null || !authUtil.verifyPassword(password, user.getPassword())) {
                logger.debug("Invalid credentials for email: {}", email);
                return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid email or password"));
            }

            logger.debug("Login successful for email: {}", email);
            String token = authUtil.generateToken(user.getId(), user.getRole());
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("token", token);
            response.put("role", user.getRole() != null ? user.getRole() : "USER");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during login: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }
    @PostMapping("/{id}/update-photo")
    public ResponseEntity<Void> updatePhoto(
        @PathVariable Long id,
        @RequestPart(value = "photo", required = true) MultipartFile photo) {
        logger.debug("Received request to update photo for user ID: {}", id);
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            if (photo != null && !photo.isEmpty()) {
                String fileName = System.currentTimeMillis() + "_" + photo.getOriginalFilename();
                Path filePath = Paths.get(uploadDir, fileName);
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, photo.getBytes());
                user.setPhoto("/api/users/photos/" + fileName);
                userRepository.save(user);
                logger.debug("Photo updated successfully for user ID: {}", id);
                return ResponseEntity.ok().build();
            } else {
                logger.debug("No photo provided for user ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            logger.error("Error updating photo: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/create-family")
    public ResponseEntity<Map<String, Object>> createFamily(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> familyData) {
        logger.debug("Received request to create family for user ID: {}", id);
        try {
            // Validate JWT token and get role
            String token = authHeader.replace("Bearer ", "");
            logger.debug("Token received: {}", token);
            logger.debug("Validating token for user ID: {}", id);
            Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
            logger.debug("Token claims: {}", claims);
            String role = (String) claims.get("role");
            logger.debug("User role from token: {}", role);
    
            logger.debug("Looking up user with ID: {}", id);
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            if (user.getFamilyId() != null) {
                logger.debug("User ID {} already belongs to a family: {}", id, user.getFamilyId());
                return ResponseEntity.badRequest().body(Map.of("error", "User already belongs to a family"));
            }

            logger.debug("Creating new family with name: {}", familyData.get("name"));
            Family family = new Family();
            family.setName(familyData.get("name"));
            Family savedFamily = familyRepository.save(family);
            logger.debug("Family created with ID: {}", savedFamily.getId());

            logger.debug("Updating user {} with new family ID: {}", id, savedFamily.getId());
            user.setFamilyId(savedFamily.getId());
            userRepository.save(user);
            
            logger.debug("Family creation completed successfully");
            Map<String, Object> response = new HashMap<>();
            response.put("familyId", savedFamily.getId());
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            logger.error("Error creating family: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error creating family: " + e.getMessage()));
        }
    }
    
    @PostMapping("/{id}/join-family/{familyId}")
    public ResponseEntity<Void> joinFamily(@PathVariable Long id, @PathVariable Long familyId) {
        logger.debug("Received request for user ID: {} to join family ID: {}", id, familyId);
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            if (user.getFamilyId() != null) {
                logger.debug("User ID {} already belongs to a family: {}", id, user.getFamilyId());
                return ResponseEntity.badRequest().build();
            }
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family == null) {
                logger.debug("Family not found for ID: {}", familyId);
                return ResponseEntity.badRequest().build();
            }
            user.setFamilyId(familyId);
            userRepository.save(user);
            logger.debug("User ID: {} joined family ID: {}", id, familyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error joining family: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> postMessage(
        @PathVariable Long id,
        @RequestPart(value = "content", required = false) String content,
        @RequestPart(value = "media", required = false) MultipartFile media,
        @RequestPart(value = "mediaType", required = false) String mediaType) {
        logger.debug("Received request to post message for user ID: {}", id);
        logger.debug("Content: {}, Media: {}, MediaType: {}", 
                    content, 
                    media != null ? media.getOriginalFilename() + " (" + media.getSize() + " bytes)" : "null", 
                    mediaType);
        
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            if (user.getFamilyId() == null) {
                logger.debug("User ID {} has no familyId, cannot post message", id);
                return ResponseEntity.badRequest().build();
            }
            Message message = new Message();
            message.setContent(content != null ? content : "");
            message.setSenderUsername(user.getUsername());
            message.setSenderId(user.getId());
            message.setFamilyId(user.getFamilyId());
            message.setTimestamp(LocalDateTime.now());

            if (media != null && mediaType != null) {
                try {
                    String originalFilename = media.getOriginalFilename();
                    String extension = "";
                    if (originalFilename != null && originalFilename.contains(".")) {
                        extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                    }
                    
                    String fileName = System.currentTimeMillis() + "_" + (originalFilename != null ? originalFilename : "media" + extension);
                    Path filePath = Paths.get(uploadDir, fileName);
                    logger.debug("Saving media to: {}", filePath);
                    
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, media.getBytes());
                    
                    // Set permissions to ensure file is accessible
                    Files.setPosixFilePermissions(filePath, 
                        java.util.Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_READ
                        )
                    );
                    
                    message.setMediaType(mediaType);
                    message.setMediaUrl("/api/users/photos/" + fileName);
                    logger.debug("Media saved successfully: {}, type: {}", fileName, mediaType);
                } catch (IOException e) {
                    logger.error("Error saving media file: {}", e.getMessage(), e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }

            messageRepository.save(message);
            logger.debug("Message posted successfully for family ID: {}", user.getFamilyId());
            return ResponseEntity.status(201).build();
        } catch (Exception e) {
            logger.error("Error posting message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long id) {
        logger.debug("Received request to get messages for user ID: {}", id);
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().body(null);
            }
            if (user.getFamilyId() == null) {
                logger.debug("User ID {} has no familyId, cannot retrieve messages", id);
                return ResponseEntity.badRequest().body(null);
            }
            List<Message> messages = messageRepository.findByFamilyId(user.getFamilyId());
            logger.debug("Found {} messages for family ID: {}", messages.size(), user.getFamilyId());
            List<Map<String, Object>> response = messages.stream().map(message -> {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("content", message.getContent());
                messageMap.put("senderUsername", message.getSenderUsername());
                messageMap.put("senderId", message.getSenderId());
                
                // Get sender's photo if available
                if (message.getSenderId() != null) {
                    logger.debug("Looking up sender with ID: {}", message.getSenderId());
                    User sender = userRepository.findById(message.getSenderId()).orElse(null);
                    if (sender != null) {
                        logger.debug("Found sender: username={}, photo={}", sender.getUsername(), sender.getPhoto());
                        messageMap.put("senderPhoto", sender.getPhoto());
                    } else {
                        logger.debug("No sender found for ID: {}", message.getSenderId());
                    }
                } else {
                    logger.debug("Message has no senderId: {}", message.getContent());
                }
                
                messageMap.put("familyId", message.getFamilyId());
                messageMap.put("timestamp", message.getTimestamp().toString());
                messageMap.put("mediaType", message.getMediaType());
                messageMap.put("mediaUrl", message.getMediaUrl());
                return messageMap;
            }).collect(Collectors.toList());
            logger.debug("Returning {} messages for family ID: {}", response.size(), user.getFamilyId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving messages: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/{id}/family-members")
    public ResponseEntity<List<Map<String, Object>>> getFamilyMembers(@PathVariable Long id) {
        logger.debug("Received request to get family members for user ID: {}", id);
        try {
            logger.debug("Querying user with ID: {}", id);
            User user = userRepository.findById(id).orElse(null);
            logger.debug("Finished querying user with ID: {}", id);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().body(null);
            }
            if (user.getFamilyId() == null) {
                logger.debug("User ID {} has no familyId", id);
                return ResponseEntity.badRequest().body(null);
            }
            logger.debug("Querying family members for family ID: {}", user.getFamilyId());
            List<User> familyMembers = userRepository.findByFamilyId(user.getFamilyId());
            logger.debug("Finished querying family members for family ID: {}", user.getFamilyId());
            List<Map<String, Object>> response = familyMembers.stream().map(member -> {
                Map<String, Object> memberMap = new HashMap<>();
                memberMap.put("id", member.getId());
                memberMap.put("username", member.getUsername());
                memberMap.put("firstName", member.getFirstName());
                memberMap.put("lastName", member.getLastName());
                memberMap.put("photo", member.getPhoto());
                return memberMap;
            }).collect(Collectors.toList());
            logger.debug("Returning {} family members for family ID: {}", response.size(), user.getFamilyId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving family members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/{id}/leave-family")
    public ResponseEntity<Void> leaveFamily(@PathVariable Long id) {
        logger.debug("Received request for user ID: {} to leave family", id);
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            if (user.getFamilyId() == null) {
                logger.debug("User ID {} is not in a family", id);
                return ResponseEntity.badRequest().build();
            }
            user.setFamilyId(null);
            userRepository.save(user);
            logger.debug("User ID: {} left their family", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error leaving family: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        logger.debug("Received request to get current user");
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            logger.debug("No userId found in request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        logger.debug("Querying user with ID: {}", userId);
        User user = userRepository.findById(userId).orElse(null);
        logger.debug("Finished querying user with ID: {}", userId);
        if (user == null) {
            logger.debug("User not found for ID: {}", userId);
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }
        logger.debug("Found user with username: {}, firstName: {}, lastName: {}, password: {}, role: {}, photo: {}, familyId: {}", 
                    user.getUsername(), user.getFirstName(), user.getLastName(), user.getPassword(), user.getRole(), user.getPhoto(), user.getFamilyId());
        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("password", user.getPassword());
        response.put("role", user.getRole() != null ? user.getRole() : "USER");
        response.put("photo", user.getPhoto());
        response.put("familyId", user.getFamilyId());
        logger.debug("Returning response: {}", response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/photos/{filename:.+}")
    public ResponseEntity<Resource> servePhoto(@PathVariable String filename, HttpServletRequest request) {
        try {
            logger.debug("Received media request for: {}", filename);
            logger.debug("Request URI: {}", request.getRequestURI());
            logger.debug("Request method: {}", request.getMethod());
            
            Path filePath = Paths.get(uploadDir, filename);
            logger.debug("Full file path: {}", filePath);
            
            if (!Files.exists(filePath)) {
                logger.error("File does not exist: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            if (!Files.isReadable(filePath)) {
                logger.error("File is not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = determineContentType(filename);
            logger.debug("Serving file with content type: {}", contentType);
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
        } catch (Exception e) {
            logger.error("Error serving media file: {}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "mp4" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "avi" -> "video/x-msvideo";
            case "wmv" -> "video/x-ms-wmv";
            case "3gp" -> "video/3gpp";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            case "flv" -> "video/x-flv";
            default -> "application/octet-stream";
        };
    }
}