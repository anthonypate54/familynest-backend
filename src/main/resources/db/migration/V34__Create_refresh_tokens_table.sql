-- V34: Create refresh tokens table for JWT refresh token functionality

-- Create refresh_tokens table
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    is_revoked BOOLEAN DEFAULT FALSE NOT NULL,
    
    -- Foreign key constraint
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_is_revoked ON refresh_tokens(is_revoked);

-- Cleanup: Remove expired or revoked refresh tokens older than 90 days
-- This can be run periodically via a cleanup job
-- DELETE FROM refresh_tokens 
-- WHERE (expires_at < CURRENT_TIMESTAMP OR is_revoked = TRUE) 
-- AND created_at < CURRENT_TIMESTAMP - INTERVAL '90 days';