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
import com.familynest.model.Invitation;
import com.familynest.repository.InvitationRepository;
import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import java.time.LocalDate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private UserFamilyMessageSettingsRepository userFamilyMessageSettingsRepository;

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
        response.put("email", user.getEmail());
        response.put("password", user.getPassword());
        response.put("role", user.getRole());
        response.put("photo", user.getPhoto());
        response.put("familyId", user.getFamilyId());
        
        // Add demographic information
        response.put("phoneNumber", user.getPhoneNumber());
        response.put("address", user.getAddress());
        response.put("city", user.getCity());
        response.put("state", user.getState());
        response.put("zipCode", user.getZipCode());
        response.put("country", user.getCountry());
        response.put("birthDate", user.getBirthDate() != null ? user.getBirthDate().toString() : null);
        response.put("bio", user.getBio());
        response.put("showDemographics", user.getShowDemographics());
        
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
                logger.debug("Username validation failed: {}", userData.getUsername());
                return ResponseEntity.badRequest().body(Map.of("error", "Username must be at least 3 characters long"));
            }
            if (userData.getEmail() == null || !userData.getEmail().contains("@")) {
                logger.debug("Email validation failed: {}", userData.getEmail());
                return ResponseEntity.badRequest().body(Map.of("error", "Valid email is required"));
            }
            if (userData.getPassword() == null || userData.getPassword().length() < 6) {
                logger.debug("Password validation failed: length less than 6");
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters long"));
            }
            if (userData.getFirstName() == null || userData.getFirstName().isEmpty()) {
                logger.debug("First name validation failed: {}", userData.getFirstName());
                return ResponseEntity.badRequest().body(Map.of("error", "First name is required"));
            }
            if (userData.getLastName() == null || userData.getLastName().isEmpty()) {
                logger.debug("Last name validation failed: {}", userData.getLastName());
                return ResponseEntity.badRequest().body(Map.of("error", "Last name is required"));
            }

            // Check if username already exists
            logger.debug("Checking if username exists: {}", userData.getUsername());
            User existingUsername = userRepository.findByUsername(userData.getUsername());
            if (existingUsername != null) {
                logger.debug("Username already exists: {}", userData.getUsername());
                return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
            }
            
            // Check if email already exists
            logger.debug("Checking if email exists: {}", userData.getEmail());
            Optional<User> existingEmailOpt = userRepository.findByEmail(userData.getEmail());
            if (existingEmailOpt.isPresent()) {
                logger.debug("Email already exists: {}", userData.getEmail());
                return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
            } else {
                logger.debug("Email is available: {}", userData.getEmail());
            }

            // Create and save the new user
            logger.debug("Creating new user with username: {}, email: {}", userData.getUsername(), userData.getEmail());
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
            logger.debug("User created successfully with ID: {}", savedUser.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("userId", savedUser.getId());
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage(), e);
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

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty() || !authUtil.verifyPassword(password, userOpt.get().getPassword())) {
                logger.debug("Invalid credentials for email: {}", email);
                return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid email or password"));
            }
            
            User user = userOpt.get();
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
            @RequestBody Map<String, String> familyData,
            HttpServletRequest request) {
        logger.debug("Received request to create family for user ID: {}", id);
        try {
            // Get the user ID and role either from test attributes or from JWT
            Long tokenUserId;
            String role;
            Object userIdAttr = request.getAttribute("userId");
            Object roleAttr = request.getAttribute("role");
            
            if (userIdAttr != null && roleAttr != null) {
                // Use the attributes set by the TestAuthFilter
                tokenUserId = (Long) userIdAttr;
                role = (String) roleAttr;
                logger.debug("Using test userId: {} and role: {}", tokenUserId, role);
            } else {
                // Normal authentication flow
                String token = authHeader.replace("Bearer ", "");
                logger.debug("Token received: {}", token);
                logger.debug("Validating token for user ID: {}", id);
                Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
                logger.debug("Token claims: {}", claims);
                tokenUserId = Long.parseLong(claims.get("userId").toString());
                role = (String) claims.get("role");
                logger.debug("User role from token: {}", role);
            }
            
            // Verify that the token user ID matches the requested user ID
            if (!tokenUserId.equals(id)) {
                logger.debug("Token user ID {} does not match request user ID {}", tokenUserId, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Not authorized to create a family for this user"));
            }
    
            logger.debug("Looking up user with ID: {}", id);
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            // Check if user already owns a family
            boolean userAlreadyOwnsFamily = false;
            Family ownedFamily = user.getOwnedFamily();
            if (ownedFamily != null) {
                logger.debug("User ID {} already owns a family: {}", id, ownedFamily.getId());
                userAlreadyOwnsFamily = true;
            }
            
            if (userAlreadyOwnsFamily) {
                return ResponseEntity.badRequest().body(Map.of("error", "User already owns a family"));
            }

            logger.debug("Creating new family with name: {}", familyData.get("name"));
            Family family = new Family();
            family.setName(familyData.get("name"));
            family.setCreatedBy(user);
            Family savedFamily = familyRepository.save(family);
            logger.debug("Family created with ID: {}", savedFamily.getId());

            logger.debug("Creating UserFamilyMembership for user ID: {} and family ID: {}", id, savedFamily.getId());
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(id);
            membership.setFamilyId(savedFamily.getId());
            membership.setActive(true);
            membership.setRole("ADMIN");
            userFamilyMembershipRepository.save(membership);
            
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
    public ResponseEntity<Map<String, Object>> joinFamily(@PathVariable Long id, @PathVariable Long familyId) {
        logger.debug("Received request for user ID: {} to join family ID: {}", id, familyId);
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            // Check if user is already a member of this family
            List<UserFamilyMembership> existingMemberships = userFamilyMembershipRepository.findByUserId(id);
            boolean alreadyMember = existingMemberships.stream()
                .anyMatch(membership -> membership.getFamilyId().equals(familyId));
            
            if (alreadyMember) {
                logger.debug("User ID: {} is already in family ID: {}", id, familyId);
                return ResponseEntity.badRequest().body(Map.of("error", "User is already in that family"));
            }
            
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family == null) {
                logger.debug("Family not found for ID: {}", familyId);
                return ResponseEntity.badRequest().body(Map.of("error", "Family not found"));
            }
            
            logger.debug("Creating UserFamilyMembership for user ID: {} and family ID: {}", id, familyId);
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(id);
            membership.setFamilyId(familyId);
            membership.setActive(true);
            membership.setRole("MEMBER");
            userFamilyMembershipRepository.save(membership);

            // If this is the user's first family, or they have no active family, set this as active
            if (user.getFamilyId() == null) {
                user.setFamilyId(familyId);
                userRepository.save(user);
            }
            
            // Create message settings (default: receive messages = true)
            logger.debug("Creating message settings for user ID: {} and family ID: {}", id, familyId);
            try {
                com.familynest.model.UserFamilyMessageSettings settings = 
                    new com.familynest.model.UserFamilyMessageSettings(id, familyId, true);
                userFamilyMessageSettingsRepository.save(settings);
                logger.debug("Message settings created successfully");
            } catch (Exception e) {
                logger.error("Error creating message settings: {}", e.getMessage());
                // Continue anyway, don't fail the whole operation
            }
            
            logger.debug("User ID: {} joined family ID: {}", id, familyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error joining family: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Error joining family: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> postMessage(
        @PathVariable Long id,
        @RequestPart(value = "content", required = false) String content,
        @RequestPart(value = "media", required = false) MultipartFile media,
        @RequestPart(value = "mediaType", required = false) String mediaType,
        @RequestPart(value = "familyId", required = false) String familyIdStr) {
        logger.debug("Received request to post message for user ID: {}", id);
        logger.debug("Content: {}, Media: {}, MediaType: {}, FamilyId: {}", 
                    content, 
                    media != null ? media.getOriginalFilename() + " (" + media.getSize() + " bytes)" : "null", 
                    mediaType,
                    familyIdStr);
        
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            
            // Determine which family ID to use for the message
            Long familyId = null;
            
            // If explicit family ID was provided in the request, use it
            if (familyIdStr != null && !familyIdStr.isEmpty()) {
                try {
                    familyId = Long.parseLong(familyIdStr);
                    logger.debug("Using explicitly provided family ID: {}", familyId);
                } catch (NumberFormatException e) {
                    logger.error("Invalid family ID format: {}", familyIdStr);
                    return ResponseEntity.badRequest().build();
                }
            } else {
                // Otherwise use the user's active family
                familyId = user.getFamilyId();
                logger.debug("Using user's active family ID: {}", familyId);
            }
            
            if (familyId == null) {
                logger.debug("No valid family ID found for message");
                return ResponseEntity.badRequest().build();
            }
            
            // Verify the user is a member of this family
            boolean isMember = userRepository.findMembersOfFamily(familyId)
                .stream()
                .anyMatch(member -> member.getId().equals(id));
                
            if (!isMember) {
                logger.debug("User {} is not a member of family {}", id, familyId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            Message message = new Message();
            message.setContent(content != null ? content : "");
            message.setSenderUsername(user.getUsername());
            message.setSenderId(user.getId());
            message.setUserId(user.getId());
            message.setFamilyId(familyId);
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
            logger.debug("Message posted successfully for family ID: {}", familyId);
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
            
            // Get all families the user belongs to
            List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(id);
            if (memberships.isEmpty()) {
                logger.debug("User ID {} is not a member of any family", id);
                return ResponseEntity.ok(List.of());
            }
            
            // Get active family ID (for backward compatibility)
            Long activeFamilyId = user.getFamilyId();
            logger.debug("User's active family ID: {}", activeFamilyId);
            
            // Get muted families (where receiveMessages = false)
            List<Long> mutedFamilyIds = userFamilyMessageSettingsRepository.findMutedFamilyIdsByUserId(id);
            logger.debug("User has muted {} families: {}", mutedFamilyIds.size(), mutedFamilyIds);
            
            // Get family IDs user wants to receive messages from
            List<Long> familyIds = memberships.stream()
                    .map(UserFamilyMembership::getFamilyId)
                    .filter(familyId -> !mutedFamilyIds.contains(familyId))
                    .collect(Collectors.toList());
            
            if (familyIds.isEmpty()) {
                logger.debug("No families to fetch messages from after filtering by preferences");
                return ResponseEntity.ok(List.of());
            }
            
            logger.debug("Fetching messages from {} families: {}", familyIds.size(), familyIds);
            
            // Get messages from all included families
            List<Message> messages = messageRepository.findByFamilyIdIn(familyIds);
            logger.debug("Found {} messages from all families", messages.size());
            
            List<Map<String, Object>> response = messages.stream().map(message -> {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("id", message.getId());
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
            
            logger.debug("Returning {} messages after filtering by message preferences", response.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving messages: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/{id}/family-members")
    public ResponseEntity<List<Map<String, Object>>> getFamilyMembers(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to get family members for user ID: {}", id);
        try {
            // Extract userId from token and validate authorization
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long tokenUserId = authUtil.extractUserId(token);
            if (tokenUserId == null) {
                logger.debug("Token validation failed or userId could not be extracted");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            
            // Only allow a user to get their own family members or members of a family they belong to
            if (!tokenUserId.equals(id)) {
                logger.debug("Token user ID {} does not match requested ID {}", tokenUserId, id);
                // Check if the token user is a member of the same family as the requested user
                User requestedUser = userRepository.findById(id).orElse(null);
                User tokenUser = userRepository.findById(tokenUserId).orElse(null);
                
                if (requestedUser == null || tokenUser == null) {
                    logger.debug("User not found for ID: {} or {}", id, tokenUserId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
                }
                
                Long requestedFamilyId = requestedUser.getFamilyId();
                
                // Check if tokenUser is a member of the requested user's family
                boolean isMemberOfSameFamily = false;
                if (requestedFamilyId != null) {
                    List<UserFamilyMembership> tokenUserMemberships = userFamilyMembershipRepository.findByUserId(tokenUserId);
                    isMemberOfSameFamily = tokenUserMemberships.stream()
                        .anyMatch(membership -> membership.getFamilyId().equals(requestedFamilyId));
                }
                
                if (!isMemberOfSameFamily) {
                    logger.debug("User {} is not authorized to view members of family for user {}", tokenUserId, id);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
                }
            }
            
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
                memberMap.put("userId", member.getId()); // Add userId field for consistency
                memberMap.put("username", member.getUsername());
                memberMap.put("firstName", member.getFirstName());
                memberMap.put("lastName", member.getLastName());
                memberMap.put("photo", member.getPhoto());
                
                // Get each member's individual family name from their family ID
                String familyName = null;
                Long memberFamilyId = member.getFamilyId();
                
                // Only include familyName in the response if the member actually has their own family
                if (memberFamilyId != null) {
                    // Get family name from the family entity
                    Family memberFamily = familyRepository.findById(memberFamilyId).orElse(null);
                    if (memberFamily != null) {
                        familyName = memberFamily.getName();
                        // Only include familyName if we actually found one
                        if (familyName != null && !familyName.isEmpty()) {
                            memberMap.put("familyName", familyName);
                        }
                    }
                }
                
                // Check if the member is a family owner
                boolean isOwner = false;
                String ownedFamilyName = null;
                
                // Find if this member has created their own family
                Family ownedFamily = familyRepository.findByCreatedById(member.getId());
                if (ownedFamily != null) {
                    isOwner = true;
                    ownedFamilyName = ownedFamily.getName();
                    
                    // Add ownership information to response
                    memberMap.put("isOwner", true);
                    memberMap.put("ownedFamilyName", ownedFamilyName);
                    
                    logger.debug("User {} is a family owner. Owned family: {}", member.getId(), ownedFamilyName);
                } else {
                    // Not an owner
                    memberMap.put("isOwner", false);
                    logger.debug("User {} is not a family owner", member.getId());
                }
                
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
        response.put("email", user.getEmail());
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

    @PostMapping("/{userId}/invite")
    public ResponseEntity<?> inviteUser(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        
        // Get the user's family
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOptional.get();
        Long familyId = user.getFamilyId();
        
        if (familyId == null) {
            return ResponseEntity.badRequest().body("User is not part of a family");
        }
        
        // Check if the invited email is already in the same family
        Optional<User> invitedUserOpt = userRepository.findByEmail(email);
        if (invitedUserOpt.isPresent()) {
            User invitedUser = invitedUserOpt.get();
            if (invitedUser.getFamilyId() != null && invitedUser.getFamilyId().equals(familyId)) {
                return ResponseEntity.badRequest().body("User is already in your family");
            }
        }
        
        // Check if there's already a pending invitation for this email to this family
        List<Invitation> pendingInvitations = invitationRepository.findByEmailAndFamilyIdAndStatus(
            email, familyId, "PENDING");
        
        if (!pendingInvitations.isEmpty()) {
            return ResponseEntity.badRequest().body("An invitation is already pending for this email");
        }
        
        // Create the invitation
        Invitation invitation = new Invitation();
        invitation.setEmail(email);
        invitation.setFamilyId(familyId);
        invitation.setSenderId(userId);
        invitation.setStatus("PENDING");
        
        // Set expiration time to 7 days from now
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        invitation.setExpiresAt(expiresAt);
        
        invitationRepository.save(invitation);
        
        return ResponseEntity.ok().build();
    }

    @GetMapping("/invitations")
    public ResponseEntity<List<Map<String, Object>>> getInvitations(@RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to get invitations");
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(List.of(Map.of("error", "Unauthorized access")));
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "User not found")));
            }

            List<Invitation> invitations = invitationRepository.findByEmail(user.getEmail());
            List<Map<String, Object>> response = invitations.stream().map(inv -> {
                Map<String, Object> invMap = new HashMap<>();
                invMap.put("id", inv.getId());
                invMap.put("familyId", inv.getFamilyId());
                invMap.put("inviterId", inv.getSenderId());
                invMap.put("status", inv.getStatus());
                invMap.put("createdAt", inv.getCreatedAt().toString());
                invMap.put("expiresAt", inv.getExpiresAt().toString());
                return invMap;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving invitations: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Error retrieving invitations: " + e.getMessage())));
        }
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<Map<String, Object>> acceptInvitation(
            @PathVariable Long invitationId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to accept invitation ID: {}", invitationId);
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access"));
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Invitation invitation = invitationRepository.findByIdAndEmail(invitationId, user.getEmail()).orElse(null);
            if (invitation == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitation not found or not intended for this user"));
            }

            if (!invitation.getStatus().equals("PENDING")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitation is no longer pending"));
            }

            if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
                invitation.setStatus("EXPIRED");
                invitationRepository.save(invitation);
                return ResponseEntity.badRequest().body(Map.of("error", "Invitation has expired"));
            }

            // Check if user is already a member of this family
            Long familyId = invitation.getFamilyId();
            List<UserFamilyMembership> existingMemberships = userFamilyMembershipRepository.findByUserId(userId);
            boolean alreadyMember = existingMemberships.stream()
                .anyMatch(membership -> membership.getFamilyId().equals(familyId));
            
            if (alreadyMember) {
                logger.debug("User ID: {} is already in family ID: {}", userId, familyId);
                invitation.setStatus("ACCEPTED");
                invitationRepository.save(invitation);
                return ResponseEntity.ok(Map.of("message", "User is already a member of this family", "familyId", familyId));
            }

            // Create a UserFamilyMembership entry to track this membership
            UserFamilyMembership membership = new UserFamilyMembership();
            membership.setUserId(userId);
            membership.setFamilyId(familyId);
            membership.setActive(true);
            membership.setRole("MEMBER");
            membership.setJoinedAt(LocalDateTime.now());
            userFamilyMembershipRepository.save(membership);

            // If user doesn't have an active family yet, set this as their active family
            if (user.getFamilyId() == null) {
                user.setFamilyId(familyId);
                userRepository.save(user);
                logger.debug("Set family ID {} as active family for user {}", familyId, userId);
            }
            
            // Create message settings (default: receive messages = true)
            logger.debug("Creating message settings for user ID: {} and family ID: {}", userId, familyId);
            try {
                com.familynest.model.UserFamilyMessageSettings settings = 
                    new com.familynest.model.UserFamilyMessageSettings(userId, familyId, true);
                userFamilyMessageSettingsRepository.save(settings);
                logger.debug("Message settings created successfully");
            } catch (Exception e) {
                logger.error("Error creating message settings: {}", e.getMessage());
                // Continue anyway, don't fail the whole operation
            }

            invitation.setStatus("ACCEPTED");
            invitationRepository.save(invitation);

            return ResponseEntity.ok(Map.of("message", "Invitation accepted", "familyId", familyId));
        } catch (Exception e) {
            logger.error("Error accepting invitation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error accepting invitation: " + e.getMessage()));
        }
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public ResponseEntity<Map<String, Object>> rejectInvitation(
            @PathVariable Long invitationId,
            @RequestHeader("Authorization") String authHeader) {
        logger.debug("Received request to reject invitation ID: {}", invitationId);
        try {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized access"));
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Invitation invitation = invitationRepository.findByIdAndEmail(invitationId, user.getEmail()).orElse(null);
            if (invitation == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitation not found or not intended for this user"));
            }

            if (!invitation.getStatus().equals("PENDING")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invitation is no longer pending"));
            }

            invitation.setStatus("REJECTED");
            invitationRepository.save(invitation);

            return ResponseEntity.ok(Map.of("message", "Invitation rejected"));
        } catch (Exception e) {
            logger.error("Error rejecting invitation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error rejecting invitation: " + e.getMessage()));
        }
    }
    
    @GetMapping("/families/{familyId}")
    public ResponseEntity<Map<String, Object>> getFamilyById(@PathVariable Long familyId) {
        logger.debug("Received request to get family details for ID: {}", familyId);
        try {
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family == null) {
                logger.debug("Family not found for ID: {}", familyId);
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", family.getId());
            response.put("name", family.getName());
            
            // Get number of members in this family
            List<User> members = userRepository.findByFamilyId(familyId);
            response.put("memberCount", members.size());
            
            logger.debug("Returning family details: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving family: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error retrieving family: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/families")
    public ResponseEntity<List<Map<String, Object>>> getUserFamilies(@PathVariable Long id) {
        logger.debug("Getting families for user ID: {}", id);
        try {
            // Check if the user exists
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "User not found")));
            }
            
            List<Map<String, Object>> families = new ArrayList<>();
            
            // Get all family memberships for this user from the user_family_membership table
            List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(id);
            logger.debug("Found {} family memberships for user {}", memberships.size(), id);
            
            // Convert each membership to a family info object
            for (UserFamilyMembership membership : memberships) {
                Long familyId = membership.getFamilyId();
                if (familyId != null) {
                    Family family = familyRepository.findById(familyId).orElse(null);
                    if (family != null) {
                        Map<String, Object> familyInfo = new HashMap<>();
                        familyInfo.put("familyId", family.getId());
                        familyInfo.put("familyName", family.getName());
                        familyInfo.put("memberCount", userRepository.findByFamilyId(familyId).size());
                        
                        // Check if user is the owner/admin of this family
                        boolean isOwner = (family.getCreatedBy() != null && family.getCreatedBy().getId().equals(id));
                        familyInfo.put("isOwner", isOwner);
                        familyInfo.put("role", membership.getRole());
                        
                        // Check if this is the user's active family
                        boolean isActive = (user.getFamilyId() != null && user.getFamilyId().equals(familyId));
                        familyInfo.put("isActive", isActive);
                        
                        families.add(familyInfo);
                    }
                }
            }
            
            // Log the results for debugging
            logger.debug("Returning {} families for user {}", families.size(), id);
            for (Map<String, Object> family : families) {
                logger.debug("Family: id={}, name={}, isOwner={}, role={}, isActive={}", 
                    family.get("familyId"), family.get("familyName"), 
                    family.get("isOwner"), family.get("role"), family.get("isActive"));
            }
            
            return ResponseEntity.ok(families);
        } catch (Exception e) {
            logger.error("Error getting families for user {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Error getting families: " + e.getMessage())));
        }
    }

    @PostMapping("/fix-family-memberships")
    public ResponseEntity<Map<String, Object>> fixFamilyMemberships() {
        logger.debug("Received request to fix family memberships");
        try {
            // Get all users
            List<User> allUsers = userRepository.findAll();
            int fixedCount = 0;
            
            for (User user : allUsers) {
                Long familyId = user.getFamilyId();
                if (familyId != null) {
                    // Check if user has a membership record
                    List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(user.getId());
                    boolean hasMembership = memberships.stream()
                            .anyMatch(m -> m.getFamilyId().equals(familyId));
                    
                    if (!hasMembership) {
                        logger.debug("Fixing family membership for user ID: {} and family ID: {}", user.getId(), familyId);
                        
                        // Get the family
                        Optional<Family> familyOpt = familyRepository.findById(familyId);
                        if (familyOpt.isPresent()) {
                            Family family = familyOpt.get();
                            
                            // Create a membership
                            UserFamilyMembership membership = new UserFamilyMembership();
                            membership.setUserId(user.getId());
                            membership.setFamilyId(familyId);
                            membership.setActive(true);
                            
                            // If this user is the creator of the family, set as ADMIN
                            if (family.getCreatedBy() != null && family.getCreatedBy().getId().equals(user.getId())) {
                                membership.setRole("ADMIN");
                            } else {
                                membership.setRole("MEMBER");
                            }
                            
                            userFamilyMembershipRepository.save(membership);
                            fixedCount++;
                        }
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Fixed family memberships",
                "fixedCount", fixedCount
            ));
        } catch (Exception e) {
            logger.error("Error fixing family memberships: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error fixing family memberships: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/profile")
    public ResponseEntity<Map<String, Object>> updateUserProfile(
            @PathVariable Long id,
            @RequestBody Map<String, Object> profileData) {
        logger.debug("Received request to update profile for user ID: {}", id);
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }
            
            // Update demographic fields
            if (profileData.containsKey("phoneNumber")) {
                user.setPhoneNumber((String) profileData.get("phoneNumber"));
            }
            if (profileData.containsKey("address")) {
                user.setAddress((String) profileData.get("address"));
            }
            if (profileData.containsKey("city")) {
                user.setCity((String) profileData.get("city"));
            }
            if (profileData.containsKey("state")) {
                user.setState((String) profileData.get("state"));
            }
            if (profileData.containsKey("zipCode")) {
                user.setZipCode((String) profileData.get("zipCode"));
            }
            if (profileData.containsKey("country")) {
                user.setCountry((String) profileData.get("country"));
            }
            if (profileData.containsKey("bio")) {
                user.setBio((String) profileData.get("bio"));
            }
            if (profileData.containsKey("showDemographics")) {
                user.setShowDemographics((Boolean) profileData.get("showDemographics"));
            }
            if (profileData.containsKey("birthDate") && profileData.get("birthDate") != null) {
                String birthDateStr = (String) profileData.get("birthDate");
                if (birthDateStr != null && !birthDateStr.isEmpty()) {
                    try {
                        LocalDate birthDate = LocalDate.parse(birthDateStr);
                        user.setBirthDate(birthDate);
                    } catch (Exception e) {
                        logger.error("Error parsing birth date: {}", e.getMessage());
                        // Continue without updating birth date
                    }
                }
            }
            
            userRepository.save(user);
            logger.debug("User profile updated successfully for ID: {}", id);
            
            // Return the updated user data
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            response.put("email", user.getEmail());
            response.put("phoneNumber", user.getPhoneNumber());
            response.put("address", user.getAddress());
            response.put("city", user.getCity());
            response.put("state", user.getState());
            response.put("zipCode", user.getZipCode());
            response.put("country", user.getCountry());
            response.put("birthDate", user.getBirthDate() != null ? user.getBirthDate().toString() : null);
            response.put("bio", user.getBio());
            response.put("showDemographics", user.getShowDemographics());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating user profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error updating user profile: " + e.getMessage()));
        }
    }

    @PostMapping("/families/{familyId}/update")
    public ResponseEntity<Map<String, Object>> updateFamily(
            @PathVariable Long familyId,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> familyData) {
        logger.debug("Received request to update family ID: {}", familyId);
        try {
            // Validate JWT token and get role
            String token = authHeader.replace("Bearer ", "");
            Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
            Long userId = Long.parseLong(claims.get("sub").toString());
            String role = (String) claims.get("role");
            logger.debug("Token validated for user ID: {}, role: {}", userId, role);
            
            // Check if family exists
            Family family = familyRepository.findById(familyId).orElse(null);
            if (family == null) {
                logger.debug("Family not found for ID: {}", familyId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Family not found"));
            }
            
            // Verify user is admin of this family
            boolean isAdmin = false;
            
            // Check if user is the creator
            if (family.getCreatedBy() != null && family.getCreatedBy().getId().equals(userId)) {
                isAdmin = true;
            } else {
                // Check if user is an admin member
                Optional<UserFamilyMembership> membership = userFamilyMembershipRepository.findByUserIdAndFamilyId(userId, familyId);
                isAdmin = membership.isPresent() && "ADMIN".equals(membership.get().getRole());
            }
            
            if (!isAdmin) {
                logger.debug("User {} is not authorized to update family {}", userId, familyId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized to update this family"));
            }
            
            // Update family name if provided
            if (familyData.containsKey("name") && familyData.get("name") != null && !familyData.get("name").trim().isEmpty()) {
                String newName = familyData.get("name").trim();
                family.setName(newName);
                familyRepository.save(family);
                logger.debug("Updated family name to: {}", newName);
            }
            
            // Return updated family details
            Map<String, Object> response = new HashMap<>();
            response.put("id", family.getId());
            response.put("name", family.getName());
            response.put("memberCount", userRepository.findByFamilyId(familyId).size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating family: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error updating family: " + e.getMessage()));
        }
    }
}