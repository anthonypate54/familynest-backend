-- Add password reset token fields to app_user table
ALTER TABLE app_user ADD COLUMN password_reset_token VARCHAR(255);
ALTER TABLE app_user ADD COLUMN password_reset_token_expires_at TIMESTAMP;
ALTER TABLE app_user ADD COLUMN password_reset_requested_at TIMESTAMP;

-- Create index for efficient password reset token lookups
CREATE INDEX idx_app_user_password_reset_token ON app_user(password_reset_token);
CREATE INDEX idx_app_user_password_reset_expires ON app_user(password_reset_token_expires_at);

-- Add constraint to ensure password reset token is unique when not null
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_password_reset_token UNIQUE (password_reset_token); 