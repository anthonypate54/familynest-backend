# Security System Migration Guide

This document provides instructions for migrating from the legacy authentication system to the new Spring Security-based authentication system.

## Overview

The new security system is a complete rewrite of the authentication and authorization mechanisms, built on Spring Security 6.x. It provides:

- Improved token management with access and refresh tokens
- Token blacklisting for revocation
- Standardized exception handling
- Comprehensive test coverage
- Proper separation of concerns with service layers
- Spring Security integration for better security guarantees

## Migration Steps

### 1. Enable the New Security System

The new security system is disabled by default. To enable it, add the following property to your `application.properties` or `application.yml` file:

```properties
security.new-system.enabled=true
```

### 2. Update Client Applications

Client applications need to be updated to handle the new token format and refresh flow:

#### Token Format

The new system returns both an access token and a refresh token:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Token Refresh

To refresh an expired access token, call the refresh endpoint with the refresh token:

```http
POST /api/auth/refresh-token
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Logout

To properly logout, call the logout endpoint with both tokens:

```http
POST /api/auth/logout
Content-Type: application/json

{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### 3. Update API Calls

The API endpoints remain the same, but the authentication mechanism has changed:

1. All requests must include the access token in the Authorization header:
   ```
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
   ```

2. Error responses now follow a standardized format:
   ```json
   {
     "error": "ERROR_CODE",
     "message": "Human-readable error message"
   }
   ```

### 4. Testing

Before deploying to production, thoroughly test the new security system:

1. Test login, registration, and token refresh flows
2. Test protected API endpoints with valid and invalid tokens
3. Test token expiration and refresh behavior
4. Test logout and token revocation

### 5. Rollback Plan

If issues are encountered with the new security system, you can disable it by setting:

```properties
security.new-system.enabled=false
```

This will revert to the legacy authentication system.

## Troubleshooting

### Common Issues

1. **401 Unauthorized**: Check that the access token is valid and not expired
2. **403 Forbidden**: Check that the user has the required permissions
3. **Invalid token**: Ensure the token is being sent in the correct format

### Logs

Enable debug logging for the security components:

```properties
logging.level.com.familynest.security=DEBUG
logging.level.org.springframework.security=DEBUG
```

## API Reference

### Authentication Endpoints

- `POST /api/auth/register`: Register a new user
- `POST /api/auth/login`: Login a user
- `POST /api/auth/refresh-token`: Refresh an access token
- `POST /api/auth/logout`: Logout a user

### Password Reset Endpoints

- `POST /api/password-reset/request`: Request a password reset
- `GET /api/password-reset/validate/{token}`: Validate a reset token
- `POST /api/password-reset/reset/{token}`: Reset a password

## Contact

For any issues or questions, please contact the development team.
