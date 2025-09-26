# FamilyNest Authentication System

This document provides an overview of the authentication system used in the FamilyNest backend application.

## Overview

The authentication system is built on Spring Security 6.x and JWT (JSON Web Tokens). It provides:

- Token-based authentication with JWT
- Secure password handling with BCrypt
- Token blacklisting for logout/revocation
- Session ID tracking for single-device enforcement
- Role-based authorization

## Key Components

### 1. JwtUtil.java

Handles all JWT operations using Spring Security 6.x compatible approaches:

- Creates JWT tokens with proper signing
- Validates tokens and extracts claims
- Supports both access tokens (short-lived) and refresh tokens (long-lived)
- Includes session ID support for single-device enforcement
- Uses modern JJWT API with secure key handling

Key methods:
- `generateAccessToken(userId, role)` - Creates a short-lived access token
- `generateRefreshToken(userId)` - Creates a long-lived refresh token
- `generateTokenPair(userId, role, sessionId)` - Creates both access and refresh tokens
- `validateTokenAndGetClaims(token)` - Validates a token and returns its claims
- `extractUserId(token)` - Extracts the user ID from a token
- `isTokenExpired(token)` - Checks if a token is expired

### 2. AuthUtil.java

Provides authentication utilities and delegates to JwtUtil:

- Handles password hashing using BCrypt
- Validates password strength
- Verifies passwords securely
- Delegates JWT operations to JwtUtil

Key methods:
- `hashPassword(rawPassword)` - Hashes a password using BCrypt
- `verifyPassword(rawPassword, encodedPassword)` - Verifies a password against its hash
- `validatePasswordStrength(password)` - Checks password complexity requirements
- `extractUserId(token)` - Delegates to JwtUtil to extract user ID

### 3. TokenBlacklistService.java

Manages revoked tokens for security:

- Keeps track of tokens that have been explicitly logged out
- Cleans up expired tokens automatically
- Provides methods to check if a token is blacklisted

Key methods:
- `blacklistToken(token)` - Adds a token to the blacklist
- `isBlacklisted(tokenId)` - Checks if a token is blacklisted
- `cleanupExpiredTokens()` - Removes expired tokens from the blacklist

### 4. AuthFilter.java

Request filter that intercepts API requests:

- Validates JWT tokens in the Authorization header
- Checks if tokens are blacklisted or expired
- Validates session IDs for single-device enforcement
- Sets user attributes for controllers to use

Key functionality:
- Intercepts all API requests
- Extracts and validates JWT tokens
- Sets user ID and role as request attributes
- Returns appropriate HTTP status codes for auth failures

### 5. AuthConfig.java

Configuration class that registers the AuthFilter with Spring Boot:

- Registers AuthFilter to intercept API requests
- Minimal configuration to avoid conflicts with main security config

## Authentication Flow

1. User logs in with username/password
2. Server validates credentials and generates JWT tokens
3. Client includes access token in Authorization header
4. AuthFilter validates token on each request
5. If token is valid, request proceeds to controllers
6. When token expires, client uses refresh token to get new tokens
7. On logout, tokens are blacklisted

## Security Features

- **Password Security**: BCrypt hashing with strength validation
- **Token Security**: Signed JWTs with expiration
- **Session Control**: Session ID tracking for single-device enforcement
- **Revocation**: Token blacklisting for immediate logout
- **Stateless**: No server-side session state (except blacklist)

## Configuration Properties

The authentication system uses the following properties:

- `jwt.secret`: Secret key for signing JWTs
- `jwt.access.expiration`: Access token expiration time in milliseconds (default: 3600000 = 1 hour)
- `jwt.refresh.expiration`: Refresh token expiration time in milliseconds (default: 2592000000 = 30 days)
- `jwt.expiration`: Legacy token expiration time (default: 86400000 = 24 hours)
- `jwt.issuer`: Issuer claim for JWTs (default: "familynest-api")
- `jwt.blacklist.cleanup.interval`: Interval for cleaning up expired blacklisted tokens (default: 3600000 = 1 hour)

## Integration with Spring Security

The authentication system integrates with Spring Security through:

1. The main `SecurityConfig.java` which configures global security settings
2. Custom `AuthFilter` which handles token validation
3. BCrypt password encoding for secure password storage

The system is designed to be permissive at the Spring Security level (allowing all requests) and then enforcing authentication in the custom filter. This approach provides flexibility while maintaining security.
