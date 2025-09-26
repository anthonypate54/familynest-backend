package com.familynest.auth;

/**
 * Represents a pair of JWT tokens: access token and refresh token
 */
public class TokenPair {
    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn; // seconds
    private final long refreshTokenExpiresIn; // seconds
    
    public TokenPair(String accessToken, String refreshToken, long accessTokenExpiresIn, long refreshTokenExpiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }
    
    public long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }
}
