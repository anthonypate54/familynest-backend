-- Create user_family_membership table if it doesn't exist
CREATE TABLE IF NOT EXISTS user_family_membership (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    role VARCHAR(50) DEFAULT 'MEMBER',
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_membership_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT uk_user_family UNIQUE (user_id, family_id)
);

-- Create index for better query performance
CREATE INDEX idx_membership_user ON user_family_membership(user_id);
CREATE INDEX idx_membership_family ON user_family_membership(family_id);
CREATE INDEX idx_membership_active ON user_family_membership(is_active);

-- Note: The original migration attempted to copy data from app_user.family_id,
-- but that column doesn't exist in the current schema. If needed, add users to families manually.

COMMENT ON TABLE user_family_membership IS 'Stores the many-to-many relationship between users and families'; 