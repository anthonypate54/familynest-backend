package com.familynest.controller;

import com.familynest.model.User;
import com.familynest.model.Family;

import com.familynest.repository.UserRepository;
import com.familynest.repository.FamilyRepository;
import com.familynest.auth.AuthUtil; // Add this import
import com.familynest.auth.TokenPair;

import com.familynest.service.MediaService;
import com.familynest.service.MessageService;
import com.familynest.service.WebSocketBroadcastService;
import com.familynest.service.PushNotificationService;
import com.familynest.service.RefreshTokenService;
import com.familynest.util.ErrorCodes; // Add ErrorCodes import
import com.familynest.service.EmailService;
import com.familynest.service.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familynest.dto.UserDataDTO;
import org.springframework.web.bind.annotation.RequestHeader;
import com.familynest.auth.JwtUtil;


import com.familynest.model.UserFamilyMembership;
import com.familynest.repository.UserFamilyMembershipRepository;
import com.familynest.repository.UserFamilyMessageSettingsRepository;
import com.familynest.repository.UserPreferencesRepository;
import com.familynest.model.UserPreferences;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private AuthUtil authUtil; // Add this dependency

    @Autowired
    private JwtUtil jwtUtil; // Ensure JwtUtil is injected

    @Autowired
    private UserFamilyMembershipRepository userFamilyMembershipRepository;

    @Autowired
    private UserFamilyMessageSettingsRepository userFamilyMessageSettingsRepository;

    @Autowired
    private UserPreferencesRepository userPreferencesRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Check if a recipient has muted the sender for family messages
     */
    private boolean isRecipientMutedBySender(Long recipientId, Long senderId) {
        try {
            String muteSql = """
                SELECT COUNT(*) > 0 
                FROM user_member_message_settings umms 
                WHERE umms.user_id = ? 
                AND umms.member_user_id = ? 
                AND umms.receive_messages = false
                """;
            Boolean isMuted = jdbcTemplate.queryForObject(muteSql, Boolean.class, recipientId, senderId);
            return isMuted != null && isMuted;
        } catch (Exception e) {
            logger.error("Error checking mute status for recipient {} and sender {}: {}", recipientId, senderId, e.getMessage());
            return false; // Default to not muted on error
        }
    }



    @Autowired
    private MediaService mediaService;

    @Autowired
    private WebSocketBroadcastService webSocketBroadcastService;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private EmailService emailService;

    /**
     * Helper method to create consistent error responses with error codes and messages
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(ErrorCodes errorCode, String message) {
        logger.debug("Creating error response - Code: {}, Message: {}", errorCode.getCode(), message);
        Map<String, Object> errorResponse = Map.of(
            "errorCode", errorCode.getCode(),
            "message", message
        );
        logger.debug("Error response created: {}", errorResponse);
        return ResponseEntity.badRequest().body(errorResponse);
    }

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

    /**
     * Public connection test endpoint for Android devices
     * Specifically designed to help test connectivity issues
     */
    @GetMapping("/connection-test")
    @CrossOrigin(origins = "*")
    public ResponseEntity<String> androidConnectionTest() {
        String html = "<!DOCTYPE html>" +
                      "<html>" +
                      "<head>" +
                      "    <title>Android Connection Test</title>" +
                      "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                      "    <style>" +
                      "        body { font-family: Arial, sans-serif; padding: 20px; }" +
                      "        .success { color: green; font-weight: bold; font-size: 24px; }" +
                      "    </style>" +
                      "</head>" +
                      "<body>" +
                      "    <h1>FamilyNest Android Test</h1>" +
                      "    <p class=\"success\">✅ CONNECTION SUCCESSFUL!</p>" +
                      "    <p>Your Android device can successfully reach the FamilyNest server.</p>" +
                      "    <p>The Flutter app should now be able to connect properly.</p>" +
                      "    <p>Server time: " + java.time.LocalDateTime.now() + "</p>" +
                      "</body>" +
                      "</html>";
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        logger.debug("Received request for user ID: {}", id);
        
        try {
            // Use a single optimized SQL query to get user data with all related information
            // Include user preferences to respect demographics visibility settings
            String sql = "WITH user_data AS (" +
                        "  SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.role, " +
                        "         u.photo, u.phone_number, u.address, u.city, " +
                        "         u.state, u.zip_code, u.country, u.birth_date, u.bio, u.onboarding_state " +
                        "  FROM app_user u WHERE u.id = ? " +
                        "), " +
                        "user_preferences AS (" +
                        "  SELECT up.show_address, up.show_phone_number, up.show_birthday " +
                        "  FROM user_preferences up WHERE up.user_id = ? " +
                        "), " +
                        "primary_family AS (" +
                        "  SELECT ufm.family_id, ufm.role as membership_role, ufm.is_active " +
                        "  FROM user_family_membership ufm " +
                        "  WHERE ufm.user_id = ? " +
                        "  ORDER BY ufm.is_active DESC, ufm.id ASC " +
                        "  LIMIT 1 " +
                        "), " +
                        "family_data AS (" +
                        "  SELECT f.id, f.name, f.created_by, " +
                        "         (SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count " +
                        "  FROM family f " +
                        "  JOIN primary_family pf ON f.id = pf.family_id " +
                        ") " +
                        "SELECT ud.*, fd.id as family_id, fd.name as family_name, " +
                        "       fd.created_by as family_created_by, fd.member_count, " +
                        "       pf.membership_role, pf.is_active, " +
                        "       COALESCE(uprefs.show_address, true) as show_address, " +
                        "       COALESCE(uprefs.show_phone_number, true) as show_phone_number, " +
                        "       COALESCE(uprefs.show_birthday, true) as show_birthday " +
                        "FROM user_data ud " +
                        "LEFT JOIN user_preferences uprefs ON 1=1 " +
                        "LEFT JOIN family_data fd ON 1=1 " +
                        "LEFT JOIN primary_family pf ON 1=1";
                        
            logger.debug("Executing optimized query for user ID: {}", id);
            
            // Execute the optimized query (now with 3 parameters: id, id, id)
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id, id, id);
            
            if (results.isEmpty()) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            
            // Process the first row for user data
            Map<String, Object> userData = results.get(0);
            
            // Create a sanitized response respecting user demographics preferences
            Map<String, Object> sanitizedUser = new HashMap<>();
            sanitizedUser.put("id", userData.get("id"));
            sanitizedUser.put("username", userData.get("username"));
            sanitizedUser.put("email", userData.get("email"));
            sanitizedUser.put("firstName", userData.get("first_name"));
            sanitizedUser.put("lastName", userData.get("last_name"));
            sanitizedUser.put("role", userData.get("role"));
            sanitizedUser.put("photo", userData.get("photo"));
            sanitizedUser.put("familyId", userData.get("family_id"));
            sanitizedUser.put("bio", userData.get("bio"));
            sanitizedUser.put("onboardingState", userData.get("onboarding_state"));
            
            // Get user preferences for demographics visibility
            Boolean showPhoneNumber = (Boolean) userData.get("show_phone_number");
            Boolean showAddress = (Boolean) userData.get("show_address");
            Boolean showBirthday = (Boolean) userData.get("show_birthday");
            
            // Only include sensitive information if user preferences allow it
            if (showPhoneNumber != null && showPhoneNumber) {
                sanitizedUser.put("phoneNumber", userData.get("phone_number"));
            }
            
            if (showAddress != null && showAddress) {
                sanitizedUser.put("address", userData.get("address"));
                sanitizedUser.put("city", userData.get("city"));
                sanitizedUser.put("state", userData.get("state"));
                sanitizedUser.put("zipCode", userData.get("zip_code"));
                sanitizedUser.put("country", userData.get("country"));
            }
            
            if (showBirthday != null && showBirthday) {
                sanitizedUser.put("birthDate", userData.get("birth_date"));
            }
            
            // Add family data if present
            if (userData.get("family_id") != null) {
                Map<String, Object> familyInfo = new HashMap<>();
                familyInfo.put("id", userData.get("family_id"));
                familyInfo.put("name", userData.get("family_name"));
                familyInfo.put("memberCount", userData.get("member_count"));
                familyInfo.put("isOwner", id.equals(userData.get("family_created_by")));
                familyInfo.put("membershipRole", userData.get("membership_role"));
                familyInfo.put("isActive", userData.get("is_active"));
                sanitizedUser.put("familyDetails", familyInfo);
            }
            
            logger.debug("Returning sanitized user data with all related information in a single query");
            return ResponseEntity.ok(sanitizedUser);
        } catch (Exception e) {
            logger.error("Error retrieving user: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
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
                return createErrorResponse(ErrorCodes.USERNAME_TOO_SHORT, "Username must be at least 3 characters long");
            }
            if (userData.getEmail() == null || !userData.getEmail().contains("@")) {
                logger.debug("Email validation failed: {}", userData.getEmail());
                return createErrorResponse(ErrorCodes.EMAIL_INVALID, "Valid email is required");
            }
            if (userData.getPassword() == null || userData.getPassword().length() < 6) {
                logger.debug("Password validation failed: length less than 6");
                return createErrorResponse(ErrorCodes.PASSWORD_TOO_SHORT, "Password must be at least 6 characters long");
            }
            if (userData.getFirstName() == null || userData.getFirstName().isEmpty()) {
                logger.debug("First name validation failed: {}", userData.getFirstName());
                return createErrorResponse(ErrorCodes.FIRST_NAME_REQUIRED, "First name is required");
            }
            if (userData.getLastName() == null || userData.getLastName().isEmpty()) {
                logger.debug("Last name validation failed: {}", userData.getLastName());
                return createErrorResponse(ErrorCodes.LAST_NAME_REQUIRED, "Last name is required");
            }

            // Check if username already exists
            logger.debug("Checking if username exists: {}", userData.getUsername());
            User existingUsername = userRepository.findByUsername(userData.getUsername());
            if (existingUsername != null) {
                logger.debug("Username already exists: {}", userData.getUsername());
                return createErrorResponse(ErrorCodes.USERNAME_ALREADY_TAKEN, "Username already taken. Please choose a different username.");
            }
            
            // Check if email already exists
            logger.debug("Checking if email exists: {}", userData.getEmail());
            Optional<User> existingEmailOpt = userRepository.findByEmail(userData.getEmail());
            if (existingEmailOpt.isPresent()) {
                logger.debug("Email already exists: {}", userData.getEmail());
                return createErrorResponse(ErrorCodes.EMAIL_ALREADY_REGISTERED, "Email already registered. Please use a different email or try logging in.");
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
            // Don't set familyId - it's managed through UserFamilyMembership
            user = userRepository.save(user);

            if (photo != null && !photo.isEmpty()) {
                // Use MediaService to handle photo upload properly
                Map<String, String> mediaResult = mediaService.uploadMedia(photo, "photo");
                user.setPhoto(mediaResult.get("mediaUrl"));
            }

            logger.debug("User created successfully with ID: {}", user.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error creating user: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody Map<String, String> loginData) {
        logger.debug("Received login request for username: {}", loginData.get("username"));
        try {
            String username = loginData.get("username");
            String password = loginData.get("password");
            
            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
            }
            
            // Convert username to lowercase for case-insensitive comparison
            username = username.toLowerCase();
            
            // First get the user with password to verify
            String userSql = "SELECT id, email, username, password, role FROM app_user WHERE LOWER(username) = ?";
            List<Map<String, Object>> userResults = jdbcTemplate.queryForList(userSql, username);
            
            if (userResults.isEmpty()) {
                logger.debug("User not found for username: {}", username);
                return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid username or password"));
            }
            
            // Get user data and verify password
            Map<String, Object> userData = userResults.get(0);
            String hashedPassword = (String) userData.get("password");
            Long userId = ((Number) userData.get("id")).longValue();
            String role = (String) userData.get("role");
            
            if (!authUtil.verifyPassword(password, hashedPassword)) {
                logger.debug("Invalid password for username: {}", username);
                return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid username or password"));
            }
            
            // Password is verified - generate token pair and return login data
            logger.debug("Login successful for username: {}", username);
            
            // SINGLE DEVICE ENFORCEMENT: Clear all existing sessions and FCM tokens
            // This ensures only one device can be logged in at a time
            logger.info("🚪 SINGLE_DEVICE: Enforcing single device login for user {}", userId);
            
            // Generate unique session ID for this login
            String sessionId = UUID.randomUUID().toString();
            logger.info("🔑 SINGLE_DEVICE: Generated session ID {} for user {}", sessionId, userId);
            
            // Invalidate all existing refresh tokens (logs out other devices)
            refreshTokenService.revokeAllUserTokens(userId);
            
            // Clear FCM token to force re-registration on this device
            String clearTokenSql = "UPDATE app_user SET fcm_token = NULL WHERE id = ?";
            int clearedTokens = jdbcTemplate.update(clearTokenSql, userId);
            if (clearedTokens > 0) {
                logger.info("🧹 SINGLE_DEVICE: Cleared FCM token for user {} - other devices will lose notifications", userId);
            }
            
            // Store session ID in database for validation
            String updateSessionSql = "UPDATE app_user SET current_session_id = ? WHERE id = ?";
            jdbcTemplate.update(updateSessionSql, sessionId, userId);
            logger.info("💾 SINGLE_DEVICE: Stored session ID for user {} - other sessions are now invalid", userId);
            
            // Generate both access and refresh tokens with session ID
            TokenPair tokenPair = jwtUtil.generateTokenPair(userId, role, sessionId);
            
            // Store refresh token in database
            refreshTokenService.createRefreshToken(userId, tokenPair.getRefreshToken());
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("token", tokenPair.getAccessToken()); // Legacy field name for backward compatibility
            response.put("accessToken", tokenPair.getAccessToken());
            response.put("refreshToken", tokenPair.getRefreshToken());
            response.put("accessTokenExpiresIn", tokenPair.getAccessTokenExpiresIn());
            response.put("refreshTokenExpiresIn", tokenPair.getRefreshTokenExpiresIn());
            response.put("tokenType", "Bearer");
            response.put("role", role != null ? role : "USER");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during login: {}", e.getMessage(), e);
            return ResponseEntity.status(400)
                .body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }
    @PostMapping("/{id}/update-photo")
    @Transactional
    public ResponseEntity<Void> updatePhoto(
        @PathVariable Long id,
        @RequestPart(value = "photo", required = true) MultipartFile photo) {
        logger.debug("Received request to update photo for user ID: {}", id);
        try {
            // Use a more efficient query that doesn't load related entities
            // Check if user exists first
            boolean userExists = userRepository.existsById(id);
            if (!userExists) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            
            if (photo != null && !photo.isEmpty()) {
                // Use MediaService to handle photo upload properly
                Map<String, String> mediaResult = mediaService.uploadMedia(photo, "photo");
                
                // Get the user and update the photo field
                User user = userRepository.findById(id).orElse(null);
                if (user != null) {
                    user.setPhoto(mediaResult.get("mediaUrl"));
                    userRepository.save(user);
                    logger.debug("Photo updated successfully for user ID: {}", id);
                    return ResponseEntity.ok().build();
                } else {
                    logger.error("Failed to update photo for user ID: {}", id);
                    return ResponseEntity.badRequest().build();
                }
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
            
            // Multi-family support: User can belong to multiple families simultaneously
            
            // Save the new membership as active
            userFamilyMembershipRepository.save(membership);
            logger.debug("Created and activated new membership for user ID: {} in family ID: {}", id, familyId);
            
            // Create message settings (default receive_messages = true)
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
            
            // Send new member notification to existing family members
            try {
                // Get user details for notification
                Map<String, Object> userData = jdbcTemplate.queryForMap(
                    "SELECT first_name, last_name FROM app_user WHERE id = ?", id
                );
                String newMemberName = userData.get("first_name") + " " + userData.get("last_name");
                
                // Get family name for notification
                Map<String, Object> familyData = jdbcTemplate.queryForMap(
                    "SELECT name FROM family WHERE id = ?", familyId
                );
                String familyName = (String) familyData.get("name");
                
                pushNotificationService.sendNewMemberNotification(familyId, newMemberName, familyName);
                logger.debug("Sent new member notification for {} joining {}", newMemberName, familyName);
            } catch (Exception e) {
                logger.error("Error sending new member notification: {}", e.getMessage());
                // Don't fail the join operation if notification fails
            }
            
            logger.debug("User ID: {} joined family ID: {}", id, familyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error joining family: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Error joining family: " + e.getMessage()));
        }
    }

    @Transactional
    @PostMapping(value = "/{userId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> postMessage(
            @RequestParam("content") String content,
            @RequestParam(value = "media", required = false) MultipartFile media,
            @RequestParam(value = "mediaType", required = false) String mediaType,
            @RequestParam(value = "videoUrl", required = false) String videoUrl,
            @RequestParam(value = "localMediaPath", required = false) String localMediaPath,
            @RequestParam(value = "familyId", required = false) Long familyId,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        try {
            // Validation
            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                       .body(Map.of("error", "Message content cannot be empty"));
            }
    
            // Extract user ID from token
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
    
            // Get user data for sender_username, etc.
            String sql = "SELECT username, first_name, last_name, photo FROM app_user WHERE id = ?";
            Map<String, Object> userData = jdbcTemplate.queryForMap(sql, userId);
    
            // Handle media upload if present
            String mediaUrl = null;
            String thumbnailUrl = null;
            
            // Handle regular media upload first (but not if we have external video URL)
            if (media != null && !media.isEmpty() && (videoUrl == null || !videoUrl.startsWith("http"))) {
                logger.error("🎬 USER CONTROLLER: About to call mediaService.uploadMedia with mediaType: {}", mediaType);
                Map<String, String> mediaResult = mediaService.uploadMedia(media, mediaType);
                mediaUrl = mediaResult.get("mediaUrl");
                if ("video".equals(mediaType)) {
                    thumbnailUrl = mediaResult.get("thumbnailUrl");
                }
            } // Handle external video URL (takes priority and may override above)
            else if (videoUrl != null && videoUrl.startsWith("http")) {
                logger.debug("Processing external video URL: {}", videoUrl);
                
                // If we uploaded media, it's actually a thumbnail for the external video
                if (media != null && !media.isEmpty() && "image".equals(mediaType)) {
                    // Use our new clean method instead of reassignment
                    Map<String, String> externalVideoResult = mediaService.processExternalVideoWithThumbnail(media, videoUrl);
                    mediaUrl = externalVideoResult.get("mediaUrl");
                    thumbnailUrl = externalVideoResult.get("thumbnailUrl");
                    mediaType = externalVideoResult.get("mediaType");
                    logger.debug("Used new method - mediaUrl: {}, thumbnailUrl: {}", mediaUrl, thumbnailUrl);
                } else {
                    // No thumbnail uploaded
                    mediaUrl = videoUrl;
                    mediaType = "cloud_video";
                    thumbnailUrl = null;
                    logger.debug("External video without thumbnail - mediaUrl: {}", mediaUrl);
                }
            }
    
            // Determine target families for the message
            List<Long> targetFamilyIds = new ArrayList<>();
            if (familyId != null) {
                // Post to specific family only
                targetFamilyIds.add(familyId);
                logger.debug("Posting message to specific family: {}", familyId);
            } else {
                // Post to ALL families the user belongs to (new behavior)
                String allFamiliesSql = "SELECT family_id FROM user_family_membership WHERE user_id = ?";
                List<Map<String, Object>> familyResults = jdbcTemplate.queryForList(allFamiliesSql, userId);
                
                if (!familyResults.isEmpty()) {
                    for (Map<String, Object> family : familyResults) {
                        targetFamilyIds.add(((Number) family.get("family_id")).longValue());
                    }
                    logger.debug("Posting message to {} families user belongs to: {}", targetFamilyIds.size(), targetFamilyIds);
                } else {
                    return ResponseEntity.badRequest()
                           .body(Map.of("error", "User is not a member of any family"));
                }
            }
    
            // Insert the message with family_id = NULL (using new schema)
            String insertSql = "INSERT INTO message (content, user_id, sender_id, sender_username, " +
                "media_type, media_url, thumbnail_url, local_media_path, family_id, like_count, love_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 0, 0) RETURNING id";
    
            logger.debug("🔨 About to INSERT message with params: content='{}', userId={}, username='{}'", 
                content, userId, userData.get("username"));
    
            Long newMessageId;
            try {
                newMessageId = jdbcTemplate.queryForObject(insertSql, Long.class,
                    content, 
                    userId, 
                    userId, 
                    userData.get("username"),
                    mediaType,
                    mediaUrl,
                    thumbnailUrl,
                    localMediaPath
                );
                logger.debug("🆔 INSERT succeeded, returned message ID: {}", newMessageId);
            } catch (Exception insertError) {
                logger.error("❌ INSERT FAILED: {}", insertError.getMessage(), insertError);
                throw insertError;
            }
            
            // Insert into message_family_link table for each target family
            String linkSql = "INSERT INTO message_family_link (message_id, family_id) VALUES (?, ?)";
            for (Long targetFamilyId : targetFamilyIds) {
                jdbcTemplate.update(linkSql, newMessageId, targetFamilyId);
                logger.debug("Linked message {} to family {}", newMessageId, targetFamilyId);
            }
            
            // Fetch the full message with all joins BEFORE committing (to avoid JDBC issues in afterCommit)
            Map<String, Object> messageDataForBroadcast = messageService.getMessageById(newMessageId);
            
            // Schedule WebSocket broadcast and notifications to happen AFTER transaction commits
            final Long finalMessageId = newMessageId;
            final List<Long> finalTargetFamilyIds = new ArrayList<>(targetFamilyIds);
            final String finalContent = content;
            final String finalSenderName = (String) userData.get("username");
            final Map<String, Object> finalMessageData = new HashMap<>(messageDataForBroadcast);
            
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        logger.error("🚀 AFTER COMMIT: Broadcasting pre-fetched message data for WebSocket");
                        
                        // Broadcast the NEW MESSAGE to all target families via WebSocket
                        for (Long targetFamilyId : finalTargetFamilyIds) {
                            logger.error("Broadcasting new message to family {} (AFTER COMMIT): {}", targetFamilyId, finalMessageData);
                            webSocketBroadcastService.broadcastNewMessage(finalMessageData, targetFamilyId);
                            
                            // Send push notifications to family members (background notifications)
                            try {
                                pushNotificationService.sendFamilyMessageNotification(finalMessageId, targetFamilyId, finalSenderName, finalContent);
                            } catch (Exception pushError) {
                                logger.error("Error sending push notification for message {} (AFTER COMMIT): {}", finalMessageId, pushError.getMessage());
                                // Don't let push notification errors break the message posting flow
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error broadcasting WebSocket message for messageId {} (AFTER COMMIT): {}", finalMessageId, e.getMessage());
                        // Continue processing - don't let WebSocket errors break message posting
                    }
                }
            });
    
            // Return the message data (reuse the already-fetched data)
            logger.debug("🔍 Returning pre-fetched message data for messageId: {}", newMessageId);
            Map<String, Object> messageData = new HashMap<>(messageDataForBroadcast);
            
            logger.debug("Successfully posted message to {} families", targetFamilyIds.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(messageData);
        } catch (Exception e) {
            logger.error("🚨 CRITICAL ERROR in postMessage: {}", e.getMessage(), e);
            logger.error("🚨 Exception class: {}", e.getClass().getName());
            if (e.getCause() != null) {
                logger.error("🚨 Root cause: {}", e.getCause().getMessage());
            }
            logger.error("🚨 Full stack trace:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to post message: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(
        @PathVariable Long id,
        @RequestHeader("Authorization") String authHeader) {

            logger.info("🔍 BACKEND HIT: Getting messages for user: {}", id);
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
         try {
            // Use a single optimized SQL query that:
            // 1. Checks if user exists
            // 2. Gets all families user belongs to
            // 3. Filters out muted families
            // 4. Gets messages from all remaining families
            // 5. Joins with sender data for display
            // 6. Uses subqueries for aggregates to avoid CTE reference issues
            String sql = "WITH user_check AS (" +
                        "  SELECT id FROM app_user WHERE id = ?" +
                        "), " +
                        "user_families AS (" +
                        "  SELECT ufm.family_id " +
                        "  FROM user_family_membership ufm " +
                        "  WHERE ufm.user_id = ? " +
                        "), " +
                        "muted_users AS (" +
                        "  SELECT umms.member_user_id " +
                        "  FROM user_member_message_settings umms " +
                        "  JOIN user_families uf ON umms.family_id = uf.family_id " +
                        "  WHERE umms.user_id = ? AND umms.receive_messages = false" +
                        "), " +
                        "message_subset AS (" +
                        "  SELECT DISTINCT m.id " +
                        "  FROM message m " +
                        "  JOIN message_family_link mfl ON m.id = mfl.message_id " +
                        "  JOIN user_families uf ON mfl.family_id = uf.family_id " +
                        "  LEFT JOIN muted_users mu ON m.sender_id = mu.member_user_id " +
                        "  WHERE mu.member_user_id IS NULL " +
                        "  ORDER BY m.id DESC " + 
                        "  LIMIT 100" +
                        ") " +
                        "SELECT DISTINCT " +
                        "  m.id, m.content, m.sender_username, m.sender_id, " +
                        "  m.timestamp, m.media_type, m.media_url, m.thumbnail_url, m.local_media_path, " +
                        "  s.photo as sender_photo, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                        "  m.like_count, m.love_count, " +
                        "  COALESCE(cc.count, 0) as comment_count, " +
                        "  COALESCE(umr.has_unread_comments, " +
                        "    CASE WHEN cc.count > 0 THEN true ELSE false END) as has_unread_comments, " +
                        "  CASE WHEN mr.id IS NOT NULL THEN true ELSE false END as is_liked, " +
                        "  CASE WHEN mr2.id IS NOT NULL THEN true ELSE false END as is_loved " +
                        "FROM message m " +
                        "JOIN message_subset ms ON m.id = ms.id " +
                        "JOIN message_family_link mfl ON m.id = mfl.message_id " +
                        "JOIN user_families uf2 ON mfl.family_id = uf2.family_id " +
                        "LEFT JOIN app_user s ON m.sender_id = s.id " +
                        "LEFT JOIN (SELECT parent_message_id, COUNT(*) as count FROM message_comment GROUP BY parent_message_id) cc " +
                        "  ON m.id = cc.parent_message_id " +
                        "LEFT JOIN user_message_read umr ON m.id = umr.message_id AND umr.user_id = ? " +
                        "LEFT JOIN message_reaction mr ON m.id = mr.message_id AND mr.user_id = ? AND mr.reaction_type = 'LIKE' AND mr.target_type = 'MESSAGE' " +
                        "LEFT JOIN message_reaction mr2 ON m.id = mr2.message_id AND mr2.user_id = ? AND mr2.reaction_type = 'LOVE' AND mr2.target_type = 'MESSAGE' " +
                        "ORDER BY m.id DESC";

            // Check if user exists first for a better error message
            if (!userRepository.existsById(id)) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().body(null);
            }
                     
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id, id, id, currentUserId, currentUserId, currentUserId);
            
            // DEBUG: Check if local_media_path is in the SQL results
            if (!results.isEmpty() && "video".equals(results.get(0).get("media_type"))) {
                logger.info("🔍 SQL Result - local_media_path: {}", results.get(0).get("local_media_path"));
            }
            
            // Debug output only for development - just log count, not each individual message
            if (logger.isDebugEnabled() && !results.isEmpty()) {
                logger.debug("Number of messages retrieved: {}", results.size());
                
                // Only log details of first message as sample
                if (results.size() > 0) {
                    Map<String, Object> firstResult = results.get(0);
                    logger.debug("Sample message data - ID: {}, timestamp: {}, has thumbnail: {}", 
                        firstResult.get("id"), 
                        firstResult.get("timestamp"),
                        firstResult.get("thumbnail_url") != null);
                }
            }
             // Transform results to the response format
            List<Map<String, Object>> response = results.stream().map(message -> {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("id", message.get("id"));
                messageMap.put("content", message.get("content"));
                messageMap.put("senderUsername", message.get("sender_username"));
                messageMap.put("senderId", message.get("sender_id"));
                messageMap.put("senderPhoto", message.get("sender_photo"));
                messageMap.put("senderFirstName", message.get("sender_first_name"));
                messageMap.put("senderLastName", message.get("sender_last_name"));
                messageMap.put("familyId", message.get("family_id"));
                messageMap.put("familyName", message.get("family_name"));
                messageMap.put("timestamp", message.get("timestamp").toString());
                messageMap.put("mediaType", message.get("media_type"));
                messageMap.put("mediaUrl", message.get("media_url"));
                messageMap.put("localMediaPath", message.get("local_media_path"));
                
                // DEBUG: Log what we're putting in the response
                if ("video".equals(message.get("media_type"))) {
                    logger.info("🎥 RESPONSE DEBUG - localMediaPath value: {}", message.get("local_media_path"));
                    logger.info("🎥 RESPONSE DEBUG - messageMap localMediaPath: {}", messageMap.get("localMediaPath"));
                }
                
                messageMap.put("likeCount", message.get("like_count"));
                messageMap.put("loveCount", message.get("love_count"));
                messageMap.put("commentCount", message.get("comment_count"));
                messageMap.put("has_unread_comments", message.get("has_unread_comments"));
                
                messageMap.put("isLiked", message.get("is_liked"));
                messageMap.put("isLoved", message.get("is_loved"));
 
                // Add thumbnail URL without excessive logging
                messageMap.put("thumbnailUrl", message.get("thumbnail_url")); 
                
                // Add video message thumbnail URL warning only once
                if ("video".equals(message.get("media_type")) && message.get("thumbnail_url") == null) {
                    logger.debug("Video message ID {} has no thumbnail_url", message.get("id"));
                }
                
                return messageMap;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} messages for user {} using a single optimized query", response.size(), id);
            
            // 🔥 DEBUG: Check final response
            if (!response.isEmpty() && "video".equals(response.get(0).get("mediaType"))) {
                logger.info("🔥 FINAL RESPONSE - First video message localMediaPath: {}", response.get(0).get("localMediaPath"));
                logger.info("🔥 FINAL RESPONSE - All keys: {}", response.get(0).keySet());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving messages: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/{id}/messages/{messageId}/mark-read")
    public ResponseEntity<Map<String, Object>> markMessageAsRead(
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String authHeader) {
        
        logger.debug("Received request to mark message {} as read for user {}", messageId, id);
        
        try {
            // Extract userId from token and validate authorization
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long currentUserId = authUtil.extractUserId(token);
            if (currentUserId == null || !currentUserId.equals(id)) {
                logger.debug("Authorization failed for user {}", id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Unauthorized"));
            }

            // Use UPSERT (INSERT ... ON CONFLICT) to handle both insert and update cases
            String upsertSql = "INSERT INTO user_message_read (user_id, message_id, has_unread_comments, created_at, updated_at) " +
                              "VALUES (?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                              "ON CONFLICT (user_id, message_id) " +
                              "DO UPDATE SET " +
                              "  has_unread_comments = false, " +
                              "  updated_at = CURRENT_TIMESTAMP";

            int rowsAffected = jdbcTemplate.update(upsertSql, id, messageId);
            
            logger.debug("Marked message {} as read for user {} (rows affected: {})", messageId, id, rowsAffected);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Message marked as read",
                "messageId", messageId,
                "userId", id
            ));
            
        } catch (Exception e) {
            logger.error("Error marking message as read: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to mark message as read"));
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
            
            // Use a single optimized SQL query to get all family data and members
            // Avoid using family_id column which doesn't exist in app_user table
            String sql = "WITH requested_user AS (" +
                        "  SELECT u.id FROM app_user u WHERE u.id = ?" +
                        "), " +
                                    "user_families AS (" +
            "  SELECT ufm.family_id " +
            "  FROM user_family_membership ufm " +
            "  WHERE ufm.user_id = ? " +
            "), " +
                        "token_user_memberships AS (" +
                        "  SELECT ufm.family_id FROM user_family_membership ufm WHERE ufm.user_id = ?" +
                        "), " +
                        "authorized AS (" +
                        "  SELECT CASE " +
                        "    WHEN ? = ? THEN true " + // tokenUserId = requestedUserId
                        "    WHEN EXISTS (SELECT 1 FROM token_user_memberships tum JOIN user_families uf ON tum.family_id = uf.family_id) THEN true " +
                        "    ELSE false " +
                        "  END as is_authorized" +
                        "), " +
                                                "family_members AS (" +
            "  SELECT DISTINCT " +
            "    u.id, u.username, u.first_name, u.last_name, u.photo, " +
            "    MIN(ufm2.family_id) as family_id, " +
            "    MIN(f.name) as family_name, MIN(f.created_by) as family_owner_id, " +
            "    MIN(ufm.role) as membership_role, " +
            "    CASE WHEN of.id IS NOT NULL THEN true ELSE false END as is_owner, " +
            "    MIN(of.name) as owned_family_name " +
            "  FROM user_families uf " +
            "  JOIN user_family_membership ufm2 ON ufm2.family_id = uf.family_id " + 
            "  JOIN app_user u ON u.id = ufm2.user_id " +
            "  JOIN family f ON f.id = uf.family_id " +
            "  LEFT JOIN user_family_membership ufm ON ufm.user_id = u.id AND ufm.family_id = uf.family_id " +
            "  LEFT JOIN family of ON of.created_by = u.id " +
            "  GROUP BY u.id, u.username, u.first_name, u.last_name, u.photo, of.id " +
            ") " +
                        "SELECT a.is_authorized, fm.* " +
                        "FROM authorized a " +
                        "CROSS JOIN family_members fm " +
                        "WHERE a.is_authorized = true";
                        
            logger.debug("Executing optimized query for family members for user ID: {}", id);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id, id, tokenUserId, tokenUserId, id);
            
            // Check authorization from first row
            if (results.isEmpty() || !(boolean)results.get(0).get("is_authorized")) {
                logger.debug("User {} is not authorized to view members of family for user {}", tokenUserId, id);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }
            // Transform results into the response format
            List<Map<String, Object>> response = results.stream().map(member -> {
                Map<String, Object> memberMap = new HashMap<>();
                memberMap.put("id", member.get("id"));
                memberMap.put("userId", member.get("id")); // Add userId field for consistency
                memberMap.put("username", member.get("username"));
                memberMap.put("firstName", member.get("first_name"));
                memberMap.put("lastName", member.get("last_name"));
                memberMap.put("photo", member.get("photo"));
                memberMap.put("familyId", member.get("family_id")); // Add the missing familyId field
                memberMap.put("familyName", member.get("family_name"));
                memberMap.put("isOwner", member.get("is_owner"));
                
                // Only include ownedFamilyName if user is an owner
                if ((boolean)member.get("is_owner") && member.get("owned_family_name") != null) {
                    memberMap.put("ownedFamilyName", member.get("owned_family_name"));
                }
                
                return memberMap;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} family members in a single query", response.size());
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
            // Check if user exists
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                logger.debug("User not found for ID: {}", id);
                return ResponseEntity.badRequest().build();
            }
            
            // Check if user is in any family
            List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(id);
            if (memberships.isEmpty()) {
                logger.debug("User ID {} is not in a family", id);
                return ResponseEntity.badRequest().build();
            }
            
            // Get the current active family (just the first one for simplicity)
            Long familyId = memberships.get(0).getFamilyId();
            
            // Update or delete the membership as needed
            // For this implementation, we'll set is_active to false instead of deleting
            for (UserFamilyMembership membership : memberships) {
                if (membership.getFamilyId().equals(familyId)) {
                    membership.setActive(false);
                    userFamilyMembershipRepository.save(membership);
                    break;
                }
            }
            
            logger.debug("User ID: {} left their family (ID: {})", id, familyId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error leaving family: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    /**
     * Get current user's preferences and settings
     * GET /api/users/current/settings
     */
    @GetMapping("/current/settings")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getCurrentUserSettings() {
        logger.debug("Received request to get current user settings");
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                logger.debug("No userId found in request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            logger.debug("Getting user preferences for user ID: {}", userId);
            
            // Get or create user preferences
            UserPreferences preferences = userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    logger.debug("Creating default preferences for user {}", userId);
                    UserPreferences newPrefs = new UserPreferences(userId);
                    return userPreferencesRepository.save(newPrefs);
                });
            
            // Build response with all user preferences
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            
            // Demographics/Privacy Settings
            response.put("showAddress", preferences.getShowAddress());
            response.put("showPhoneNumber", preferences.getShowPhoneNumber());
            response.put("showBirthday", preferences.getShowBirthday());
            
            // Notification Preferences
            response.put("familyMessagesNotifications", preferences.getFamilyMessagesNotifications());
            response.put("newMemberNotifications", preferences.getNewMemberNotifications());
            response.put("invitationNotifications", preferences.getInvitationNotifications());
            
            logger.debug("Returning user preferences: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving user preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error retrieving user preferences: " + e.getMessage()));
        }
    }

    /**
     * Update current user's preferences
     * PUT /api/users/current/preferences
     */
    @PutMapping("/current/preferences")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateUserPreferences(@RequestBody Map<String, Object> preferencesData) {
        logger.debug("Received request to update user preferences");
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                logger.debug("No userId found in request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            logger.debug("Updating preferences for user ID: {} with data: {}", userId, preferencesData);
            
            // Get or create user preferences
            UserPreferences preferences = userPreferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    logger.debug("Creating new preferences for user {}", userId);
                    return new UserPreferences(userId);
                });
            
            // Update demographics/privacy settings
            if (preferencesData.containsKey("showAddress")) {
                preferences.setShowAddress((Boolean) preferencesData.get("showAddress"));
            }
            if (preferencesData.containsKey("showPhoneNumber")) {
                preferences.setShowPhoneNumber((Boolean) preferencesData.get("showPhoneNumber"));
            }
            if (preferencesData.containsKey("showBirthday")) {
                preferences.setShowBirthday((Boolean) preferencesData.get("showBirthday"));
            }
            
            // Update notification preferences
            if (preferencesData.containsKey("familyMessagesNotifications")) {
                preferences.setFamilyMessagesNotifications((Boolean) preferencesData.get("familyMessagesNotifications"));
            }
            if (preferencesData.containsKey("newMemberNotifications")) {
                preferences.setNewMemberNotifications((Boolean) preferencesData.get("newMemberNotifications"));
            }
            if (preferencesData.containsKey("invitationNotifications")) {
                preferences.setInvitationNotifications((Boolean) preferencesData.get("invitationNotifications"));
            }
            
            // Save preferences
            preferences = userPreferencesRepository.save(preferences);
            logger.debug("User preferences updated successfully for user ID: {}", userId);
            
            // Also update the user_notification_matrix table for consistency
            if (preferencesData.containsKey("invitationNotifications")) {
                Boolean invitationNotifications = (Boolean) preferencesData.get("invitationNotifications");
                String updateMatrixSql = """
                    UPDATE user_notification_matrix 
                    SET invitations_push = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = ? AND family_id = 0 AND member_id = 0
                """;
                int rowsUpdated = jdbcTemplate.update(updateMatrixSql, invitationNotifications, userId);
                logger.debug("Updated {} matrix rows for user {} invitation notifications: {}", rowsUpdated, userId, invitationNotifications);
                
                // If no global matrix row exists, create one
                if (rowsUpdated == 0) {
                    String insertMatrixSql = """
                        INSERT INTO user_notification_matrix (
                            user_id, family_id, member_id, push_enabled, device_permission_granted,
                            family_messages_push, dm_messages_push, invitations_push, 
                            reactions_push, comments_push, new_member_push,
                            family_messages_websocket, dm_messages_websocket, invitations_websocket,
                            reactions_websocket, comments_websocket, new_member_websocket
                        ) VALUES (?, 0, 0, TRUE, TRUE, TRUE, TRUE, ?, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE)
                    """;
                    jdbcTemplate.update(insertMatrixSql, userId, invitationNotifications);
                    logger.debug("Created new matrix row for user {} with invitation notifications: {}", userId, invitationNotifications);
                }
            }
            
            // Build response with updated preferences
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("showAddress", preferences.getShowAddress());
            response.put("showPhoneNumber", preferences.getShowPhoneNumber());
            response.put("showBirthday", preferences.getShowBirthday());
            response.put("familyMessagesNotifications", preferences.getFamilyMessagesNotifications());
            response.put("newMemberNotifications", preferences.getNewMemberNotifications());
            response.put("invitationNotifications", preferences.getInvitationNotifications());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating user preferences: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error updating user preferences: " + e.getMessage()));
        }
    }

    @GetMapping("/current")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        logger.debug("Received request to get current user");
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                logger.debug("No userId found in request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }
            
            // Use a single optimized SQL query that returns everything in one go
            String sql = "WITH user_data AS (" +
                        "  SELECT u.id, u.username, u.email, u.first_name, u.last_name, u.role, u.photo, u.onboarding_state " +
                        "  FROM app_user u WHERE u.id = ? " +
                        "), " +
                        "membership_data AS (" +
                        "  SELECT ufm.family_id, ufm.role as membership_role, ufm.is_active, " +
                        "         f.name as family_name, f.created_by as family_created_by " +
                        "  FROM user_family_membership ufm " +
                        "  JOIN family f ON ufm.family_id = f.id " +
                        "  WHERE ufm.user_id = ? " +
                        "  ORDER BY ufm.is_active DESC, ufm.id ASC " +
                        "  LIMIT 1 " +
                        ") " +
                        "SELECT ud.*, md.family_id, md.family_name, " +
                        "       md.membership_role, md.is_active, " +
                        "       (md.family_created_by = ud.id) as is_family_owner " +
                        "FROM user_data ud " +
                        "LEFT JOIN membership_data md ON 1=1";
            
            logger.debug("Executing optimized single query for current user ID: {}", userId);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, userId);
            
            if (results.isEmpty()) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
            
            // Transform the results into a response
            Map<String, Object> userData = results.get(0);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userData.get("id"));
            response.put("username", userData.get("username"));
            response.put("firstName", userData.get("first_name"));
            response.put("lastName", userData.get("last_name"));
            response.put("email", userData.get("email"));
            response.put("role", userData.get("role") != null ? userData.get("role") : "USER");
            response.put("photo", userData.get("photo"));
            response.put("familyId", userData.get("family_id"));
            response.put("onboardingState", userData.get("onboarding_state"));
            
            logger.debug("***Returning current user data from single query: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving current user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error retrieving current user: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/families")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getUserFamilies(@PathVariable Long id) {
        logger.debug("Getting families for user ID: {}", id);
        try {
            // Check if the user exists using a lightweight check
            if (!userRepository.existsById(id)) {
                return ResponseEntity.badRequest().body(List.of(Map.of("error", "User not found")));
            }
            
            // Use a single optimized SQL query to get all family data with one database call
            // Remove the LIMIT 1 to get ALL families the user belongs to
            String sql = "WITH user_memberships AS (" +
                        "  SELECT " +
                        "    ufm.family_id, ufm.role, ufm.is_active " +
                        "  FROM user_family_membership ufm " +
                        "  WHERE ufm.user_id = ? " +
                        ") " +
                        "SELECT " +
                        "  f.id as family_id, f.name as family_name, f.created_by as owner_id, " +
                        "  (SELECT COUNT(*) FROM user_family_membership ufm WHERE ufm.family_id = f.id) as member_count, " +
                        "  um.role, um.is_active, " +
                        "  CASE WHEN f.created_by = ? THEN true ELSE false END as is_owner " +
                        "FROM family f " +
                        "JOIN user_memberships um ON f.id = um.family_id " +
                        "ORDER BY um.is_active DESC, f.name ASC";
            
            logger.debug("Executing optimized query for families for user ID: {}", id);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, id, id);
            
            // Transform results into the response format  
            List<Map<String, Object>> families = results.stream().map(family -> {
                Map<String, Object> familyInfo = new HashMap<>();
                familyInfo.put("familyId", family.get("family_id"));
                familyInfo.put("familyName", family.get("family_name"));
                familyInfo.put("memberCount", family.get("member_count"));
                familyInfo.put("isOwner", family.get("is_owner"));
                familyInfo.put("role", family.get("role"));
                familyInfo.put("isActive", family.get("is_active"));
                return familyInfo;
            }).collect(Collectors.toList());
            
            logger.debug("Returning {} families for user {} with a single optimized query", families.size(), id);
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
            // This method needs to be completely reimplemented since the app_user table no longer has a family_id column
            // Instead, we'll ensure that users have at most one active family membership
            
            // Get all users
            List<User> allUsers = userRepository.findAll();
            int fixedCount = 0;
            
            for (User user : allUsers) {
                // Get all family memberships for this user
                List<UserFamilyMembership> memberships = userFamilyMembershipRepository.findByUserId(user.getId());
                
                // Skip users with no memberships
                if (memberships.isEmpty()) {
                    continue;
                }
                
                // Count active memberships
                long activeCount = memberships.stream()
                    .filter(UserFamilyMembership::isActive)
                    .count();
                
                // If user has no active memberships but has memberships, make the first one active
                if (activeCount == 0) {
                    UserFamilyMembership firstMembership = memberships.get(0);
                    firstMembership.setActive(true);
                    userFamilyMembershipRepository.save(firstMembership);
                    fixedCount++;
                    logger.debug("Fixed: Set active membership for user ID: {} to family ID: {}", 
                        user.getId(), firstMembership.getFamilyId());
                } 
                // If user has multiple active memberships, keep only the first one active
                else if (activeCount > 1) {
                    boolean foundFirst = false;
                    for (UserFamilyMembership membership : memberships) {
                        if (membership.isActive()) {
                            if (!foundFirst) {
                                foundFirst = true;
                            } else {
                                membership.setActive(false);
                                userFamilyMembershipRepository.save(membership);
                                fixedCount++;
                                logger.debug("Fixed: Deactivated extra membership for user ID: {} in family ID: {}", 
                                    user.getId(), membership.getFamilyId());
                            }
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
            
            // Update personal details
            if (profileData.containsKey("firstName")) {
                user.setFirstName((String) profileData.get("firstName"));
            }
            if (profileData.containsKey("lastName")) {
                user.setLastName((String) profileData.get("lastName"));
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
            // showDemographics field removed - now handled by user_preferences table
            if (profileData.containsKey("birthDate") && profileData.get("birthDate") != null) {
                String birthDateStr = (String) profileData.get("birthDate");
                if (birthDateStr != null && !birthDateStr.isEmpty()) {
                    try {
                        LocalDate birthDate;
                        // Try different date formats
                        if (birthDateStr.contains("/")) {
                            // Handle MM/dd/yyyy format
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
                            birthDate = LocalDate.parse(birthDateStr, formatter);
                        } else {
                            // Handle yyyy-MM-dd format (ISO)
                            birthDate = LocalDate.parse(birthDateStr);
                        }
                        user.setBirthDate(birthDate);
                    } catch (DateTimeParseException e) {
                        logger.error("Error parsing birth date '{}': {}", birthDateStr, e.getMessage());
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
            // showDemographics field removed - now handled by user_preferences table
            
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

    @GetMapping("/invitations")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getInvitations(@RequestHeader(value = "Authorization", required = true) String authHeader) {
        logger.info("🔍 INVITATIONS: Request received - authHeader present: {}", authHeader != null);
        logger.debug("Received request to get invitations");
        try {
            if (authHeader == null || authHeader.trim().isEmpty()) {
                return ResponseEntity.status(403).body(List.of(Map.of("error", "Missing authorization header")));
            }
    
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            Long userId = authUtil.extractUserId(token);
            if (userId == null) {
                return ResponseEntity.status(403).body(List.of(Map.of("error", "Unauthorized access")));
            }
    
            // Simplified SQL query for debugging
            String sql = "SELECT i.id, i.family_id, i.sender_id, i.status, i.created_at, i.expires_at, i.email, " +
                        "       f.name as family_name, " +
                        "       s.username as sender_username, s.first_name as sender_first_name, s.last_name as sender_last_name, " +
                        "       u.email as user_email " +
                        "FROM invitation i " +
                        "LEFT JOIN family f ON i.family_id = f.id " +
                        "LEFT JOIN app_user s ON i.sender_id = s.id " +
                        "LEFT JOIN app_user u ON u.id = ? " +
                        "WHERE i.email = (SELECT email FROM app_user WHERE id = ?)";
            
            logger.debug("Executing query for invitations for user ID: {}", userId);
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId, userId);
  // Add this after the SQL query execution
logger.info("🔍 DEBUG: Raw results count: {}", results.size());
String emailCheckSql = "SELECT email FROM app_user WHERE id = ?";
List<String> userEmails = jdbcTemplate.queryForList(emailCheckSql, String.class, userId);
logger.info("🔍 DEBUG: User {} email: {}", userId, userEmails.isEmpty() ? "NOT FOUND" : userEmails.get(0));
           
           // Transform results into the response format
            List<Map<String, Object>> response = results.stream().map(inv -> {
                Map<String, Object> invMap = new HashMap<>();
                invMap.put("id", inv.get("id"));
                invMap.put("familyId", inv.get("family_id"));
                invMap.put("inviterId", inv.get("sender_id"));
                invMap.put("status", inv.get("status"));
                invMap.put("createdAt", inv.get("created_at").toString());
                invMap.put("expiresAt", inv.get("expires_at").toString());
                
                // Add additional information
                invMap.put("familyName", inv.get("family_name"));
                invMap.put("senderName", inv.get("sender_first_name") + " " + inv.get("sender_last_name"));
                invMap.put("senderUsername", inv.get("sender_username"));
                
                // Debug info
                invMap.put("invitationEmail", inv.get("email"));
                invMap.put("userEmail", inv.get("user_email"));
                
                return invMap;
            }).collect(Collectors.toList());
  
            
            logger.debug("Returning {} invitations for user ID: {}", response.size(), userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving invitations: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Error retrieving invitations: " + e.getMessage())));
        }
    }

    /**
     * Register FCM token for push notifications
     */
    @PostMapping("/{userId}/fcm-token")
    public ResponseEntity<Map<String, Object>> registerFcmToken(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            String fcmToken = request.get("fcmToken");
            if (fcmToken == null || fcmToken.trim().isEmpty()) {
                logger.warn("🚨 FCM_REGISTER: Empty or null token received for user {}", userId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "FCM token is required"));
            }

            logger.info("🔑 FCM_REGISTER: Registering FCM token for user {}", userId);
            logger.info("🔑 FCM_REGISTER: Token length: {}", fcmToken.length());
            logger.info("🔑 FCM_REGISTER: Token prefix: {}", fcmToken.substring(0, Math.min(30, fcmToken.length())));
            
            // Detect token type
            if (fcmToken.startsWith("ios_simulator_mock_token_")) {
                logger.warn("🧪 FCM_REGISTER: MOCK SIMULATOR TOKEN detected for user {} - this should not happen on real device!", userId);
            } else if (fcmToken.contains(":") && fcmToken.length() > 100) {
                logger.info("✅ FCM_REGISTER: Real FCM token detected for user {} (length: {})", userId, fcmToken.length());
            } else {
                logger.warn("⚠️ FCM_REGISTER: Unknown token format for user {} (length: {})", userId, fcmToken.length());
            }

            // Clear this token from any other users first (prevent reuse)
            String clearOldSql = "UPDATE app_user SET fcm_token = NULL WHERE fcm_token = ? AND id != ?";
            int clearedRows = jdbcTemplate.update(clearOldSql, fcmToken, userId);
            if (clearedRows > 0) {
                logger.info("🔄 FCM_REGISTER: Cleared token from {} other users before registering to user {}", clearedRows, userId);
            }
            
            // Update user's FCM token
            String updateSql = "UPDATE app_user SET fcm_token = ? WHERE id = ?";
            int rowsUpdated = jdbcTemplate.update(updateSql, fcmToken, userId);

            if (rowsUpdated == 0) {
                logger.error("❌ FCM_REGISTER: User {} not found in database", userId);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "User not found"));
            }

            logger.info("✅ FCM_REGISTER: Successfully updated FCM token for user {}, rows updated: {}", userId, rowsUpdated);
            return ResponseEntity.ok(Map.of(
                "message", "FCM token registered successfully",
                "userId", userId
            ));

        } catch (Exception e) {
            logger.error("💥 FCM_REGISTER: Error registering FCM token for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to register FCM token: " + e.getMessage()));
        }
    }

    /**
     * Debug endpoint to test FCM token reception
     */
    @PostMapping("/{userId}/fcm-token-debug")
    public ResponseEntity<Map<String, Object>> debugFcmToken(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            String fcmToken = request.get("fcmToken");
            
            logger.info("🔍 FCM_DEBUG: ================== DEBUG ENDPOINT CALLED ==================");
            logger.info("🔍 FCM_DEBUG: User ID: {}", userId);
            logger.info("🔍 FCM_DEBUG: Request body: {}", request);
            
            if (fcmToken == null) {
                logger.warn("🔍 FCM_DEBUG: FCM token is NULL!");
                return ResponseEntity.ok(Map.of("status", "NULL_TOKEN", "userId", userId));
            }
            
            if (fcmToken.trim().isEmpty()) {
                logger.warn("🔍 FCM_DEBUG: FCM token is EMPTY!");
                return ResponseEntity.ok(Map.of("status", "EMPTY_TOKEN", "userId", userId));
            }
            
            logger.info("🔍 FCM_DEBUG: Token received successfully");
            logger.info("🔍 FCM_DEBUG: Token length: {}", fcmToken.length());
            logger.info("🔍 FCM_DEBUG: Token first 30 chars: {}", fcmToken.substring(0, Math.min(30, fcmToken.length())));
            logger.info("🔍 FCM_DEBUG: Token last 30 chars: {}", fcmToken.substring(Math.max(0, fcmToken.length() - 30)));
            
            // Detect token type
            if (fcmToken.startsWith("ios_simulator_mock_token_")) {
                logger.warn("🔍 FCM_DEBUG: 🧪 MOCK SIMULATOR TOKEN detected!");
                logger.warn("🔍 FCM_DEBUG: This means device detection failed on real device!");
                return ResponseEntity.ok(Map.of(
                    "status", "MOCK_TOKEN_DETECTED", 
                    "tokenType", "simulator_mock",
                    "userId", userId,
                    "length", fcmToken.length()
                ));
            } else if (fcmToken.contains(":") && fcmToken.length() > 100) {
                logger.info("🔍 FCM_DEBUG: ✅ REAL FCM TOKEN detected!");
                logger.info("🔍 FCM_DEBUG: This is a proper Firebase FCM token");
                return ResponseEntity.ok(Map.of(
                    "status", "REAL_TOKEN_DETECTED", 
                    "tokenType", "real_fcm",
                    "userId", userId,
                    "length", fcmToken.length()
                ));
            } else {
                logger.warn("🔍 FCM_DEBUG: ⚠️ UNKNOWN TOKEN FORMAT!");
                logger.warn("🔍 FCM_DEBUG: Not mock, not standard FCM - investigate!");
                return ResponseEntity.ok(Map.of(
                    "status", "UNKNOWN_TOKEN_FORMAT", 
                    "tokenType", "unknown",
                    "userId", userId,
                    "length", fcmToken.length()
                ));
            }
            
        } catch (Exception e) {
            logger.error("🔍 FCM_DEBUG: 💥 ERROR in debug endpoint: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Debug endpoint failed: " + e.getMessage()));
        } finally {
            logger.info("🔍 FCM_DEBUG: ================== DEBUG ENDPOINT COMPLETE ==================");
        }
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> requestData) {
        logger.debug("Received request for password reset");
        try {
            String email = requestData.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            // Convert email to lowercase for case-insensitive comparison
            email = email.toLowerCase();
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                logger.debug("User not found for email: {}", email);
                // Return success even if user not found to prevent email enumeration
                return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a password reset link has been sent."));
            }

            User user = userOpt.get();
            // Generate a reset token
            String resetToken = UUID.randomUUID().toString();
            LocalDateTime expiryDate = LocalDateTime.now().plusHours(24); // Token expires in 24 hours

            // Update user with reset token and expiry
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiresAt(expiryDate);
            user.setPasswordResetRequestedAt(LocalDateTime.now());
            userRepository.save(user);

            // Send actual email instead of just logging
            try {
                emailService.sendPasswordResetEmail(email, resetToken);
                logger.info("Password reset email sent to: {}", email);
            } catch (Exception e) {
                logger.error("Failed to send password reset email: {}", e.getMessage());
                // Still return success to prevent revealing if email exists
            }

            return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a password reset link has been sent."));
        } catch (Exception e) {
            logger.error("Error processing password reset: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An error occurred while processing your request."));
        }
    }

    @PostMapping("/forgot-username")
    public ResponseEntity<Map<String, Object>> forgotUsername(@RequestBody Map<String, String> requestData) {
        logger.debug("Received request for username reminder");
        try {
            String email = requestData.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            // Convert email to lowercase for case-insensitive comparison
            email = email.toLowerCase();
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                logger.debug("User not found for email: {}", email);
                // Return success even if user not found to prevent email enumeration
                return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a username reminder has been sent."));
            }

            User user = userOpt.get();
            
            // Send username reminder email
            try {
                emailService.sendUsernameReminderEmail(email, user.getUsername());
                logger.info("Username reminder email sent to: {}", email);
            } catch (Exception e) {
                logger.error("Failed to send username reminder email: {}", e.getMessage());
                // Still return success to prevent revealing if email exists
            }

            return ResponseEntity.ok(Map.of("message", "If an account exists with this email, a username reminder has been sent."));
        } catch (Exception e) {
            logger.error("Error processing username reminder: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An error occurred while processing your request."));
        }
    }

    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> requestData, HttpServletRequest request) {
        logger.debug("Received request to change password");
        try {
            // Get user ID from request (set by AuthFilter)
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                logger.debug("No userId found in request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            String currentPassword = requestData.get("currentPassword");
            String newPassword = requestData.get("newPassword");

            if (currentPassword == null || currentPassword.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is required"));
            }

            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 6 characters long"));
            }

            if (currentPassword.equals(newPassword)) {
                return ResponseEntity.badRequest().body(Map.of("error", "New password must be different from current password"));
            }

            // Get user from database
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                logger.debug("User not found for ID: {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            // Verify current password
            if (!authUtil.verifyPassword(currentPassword, user.getPassword())) {
                logger.debug("Invalid current password for user: {}", userId);
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
            }

            // Update password
            user.setPassword(authUtil.hashPassword(newPassword));
            userRepository.save(user);

            logger.info("Password changed successfully for user: {}", userId);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));

        } catch (Exception e) {
            logger.error("Error changing password: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An error occurred while changing password"));
        }
    }

    /**
     * Simple debug endpoint - logs any string from frontend
     */
    @PostMapping("/test/print")
    public ResponseEntity<Map<String, Object>> printFromFrontEnd(
            @RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            
            logger.error("🖨️ FRONTEND_DEBUG: {}", message);
            
            return ResponseEntity.ok(Map.of(
                "status", "logged",
                "message", "Message logged successfully"
            ));
            
        } catch (Exception e) {
            logger.error("💥 FRONTEND_DEBUG: Error logging message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to log message: " + e.getMessage()));
        }
    }




}