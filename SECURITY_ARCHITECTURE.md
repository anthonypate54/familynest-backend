# Security System Architecture

This document describes the architecture of the new security system for the FamilyNest application.

## Overview

The new security system is built on Spring Security 6.x and follows industry best practices for authentication and authorization. It uses JWT (JSON Web Tokens) for stateless authentication and provides a robust token management system with access and refresh tokens.

## Components

### 1. JWT Service

The `JwtService` is responsible for generating, validating, and parsing JWT tokens. It provides:

- Token generation with configurable expiration times
- Token validation against user details
- Claim extraction from tokens
- Support for both access and refresh tokens

### 2. Token Blacklisting

The `TokenBlacklistService` maintains a list of revoked tokens to prevent their use after logout or compromise. It:

- Blacklists tokens by their unique ID
- Checks if a token is blacklisted during authentication
- Automatically removes expired tokens from the blacklist

### 3. Authentication Filter

The `JwtAuthenticationFilter` intercepts incoming requests and:

- Extracts the JWT token from the Authorization header
- Validates the token using the JwtService
- Checks if the token is blacklisted
- Sets up the Spring Security context for authenticated requests

### 4. Security Configuration

The `SecurityConfig` class configures Spring Security with:

- Stateless session management
- CSRF protection
- CORS configuration
- URL-based authorization rules
- Authentication provider integration

### 5. Authentication Service

The `AuthenticationService` orchestrates the authentication process:

- User registration with validation
- User login with credential verification
- Token refresh with blacklisting of old tokens
- Logout with token revocation

### 6. User Service

The `UserService` provides user-related operations:

- Loading user details for authentication
- Finding users by username or email
- Password management with secure hashing
- User profile management

### 7. Exception Handling

The `SecurityException` class provides standardized error handling for security-related issues:

- Consistent error codes and messages
- HTTP status code mapping
- Factory methods for common security exceptions

## Authentication Flow

### 1. Registration

```
Client                                                  Server
  |                                                       |
  |  POST /api/auth/register                              |
  |  {username, password, email, firstName, lastName}     |
  |------------------------------------------------------>|
  |                                                       | Validate input
  |                                                       | Check if username/email exists
  |                                                       | Hash password
  |                                                       | Create user
  |                                                       | Generate tokens
  |  200 OK                                               |
  |  {accessToken, refreshToken}                          |
  |<------------------------------------------------------|
```

### 2. Login

```
Client                                                  Server
  |                                                       |
  |  POST /api/auth/login                                 |
  |  {username, password}                                 |
  |------------------------------------------------------>|
  |                                                       | Authenticate credentials
  |                                                       | Generate tokens
  |  200 OK                                               |
  |  {accessToken, refreshToken}                          |
  |<------------------------------------------------------|
```

### 3. Authenticated Request

```
Client                                                  Server
  |                                                       |
  |  GET /api/resource                                    |
  |  Authorization: Bearer {accessToken}                  |
  |------------------------------------------------------>|
  |                                                       | Extract token
  |                                                       | Validate token
  |                                                       | Check if blacklisted
  |                                                       | Set security context
  |                                                       | Process request
  |  200 OK                                               |
  |  {resourceData}                                       |
  |<------------------------------------------------------|
```

### 4. Token Refresh

```
Client                                                  Server
  |                                                       |
  |  POST /api/auth/refresh-token                         |
  |  {refreshToken}                                       |
  |------------------------------------------------------>|
  |                                                       | Validate refresh token
  |                                                       | Check if blacklisted
  |                                                       | Blacklist old token
  |                                                       | Generate new tokens
  |  200 OK                                               |
  |  {accessToken, refreshToken}                          |
  |<------------------------------------------------------|
```

### 5. Logout

```
Client                                                  Server
  |                                                       |
  |  POST /api/auth/logout                                |
  |  {accessToken, refreshToken}                          |
  |------------------------------------------------------>|
  |                                                       | Blacklist tokens
  |  200 OK                                               |
  |  {message: "Logout successful"}                       |
  |<------------------------------------------------------|
```

## Token Structure

### Access Token

```json
{
  "sub": "username",
  "userId": 123,
  "role": "USER",
  "tokenType": "ACCESS",
  "iat": 1632150000,
  "exp": 1632153600,
  "jti": "unique-token-id"
}
```

### Refresh Token

```json
{
  "sub": "username",
  "userId": 123,
  "role": "USER",
  "tokenType": "REFRESH",
  "iat": 1632150000,
  "exp": 1632236400,
  "jti": "unique-token-id"
}
```

## Security Considerations

### 1. Token Expiration

- Access tokens expire after a short period (default: 15 minutes)
- Refresh tokens have a longer lifetime (default: 7 days)
- Expiration times are configurable in application properties

### 2. Token Storage

- Tokens should be stored securely on the client side
- Access tokens can be stored in memory
- Refresh tokens should be stored in secure HTTP-only cookies or secure storage

### 3. HTTPS

- All communication should be over HTTPS to prevent token interception

### 4. CSRF Protection

- CSRF protection is disabled for stateless JWT authentication
- The stateless nature of JWT tokens provides inherent protection against CSRF attacks

### 5. Password Security

- Passwords are hashed using BCrypt with a work factor of 10
- Password strength validation is enforced during registration and password changes

## Configuration

The security system can be configured through application properties:

```properties
# JWT Configuration
jwt.secret=your-secret-key
jwt.access-token-expiration=900000  # 15 minutes in milliseconds
jwt.refresh-token-expiration=604800000  # 7 days in milliseconds

# Feature Toggle
security.new-system.enabled=true

# CORS Configuration
cors.allowed-origins=*
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed-headers=*
cors.allow-credentials=true
```

## Testing

The security system includes comprehensive tests:

- Unit tests for individual components
- Integration tests for authentication flows
- End-to-end tests for protected resources

## Future Improvements

1. **Redis Integration**: Replace in-memory token blacklist with Redis for distributed deployments
2. **Role-Based Access Control**: Enhance authorization with fine-grained role-based permissions
3. **OAuth2 Integration**: Support third-party authentication providers
4. **Rate Limiting**: Implement rate limiting for authentication endpoints to prevent brute force attacks
5. **Audit Logging**: Add comprehensive audit logging for security events





