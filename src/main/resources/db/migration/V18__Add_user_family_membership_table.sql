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

-- Migrate existing user-family relationships to the new table
INSERT INTO user_family_membership (user_id, family_id, is_active, role)
SELECT id, family_id, TRUE, 'MEMBER'
FROM app_user
WHERE family_id IS NOT NULL
ON CONFLICT (user_id, family_id) DO NOTHING;

-- Add ADMIN role for users who created families
UPDATE user_family_membership m
SET role = 'ADMIN'
FROM family f
WHERE m.family_id = f.id AND m.user_id = f.created_by;

-- We won't remove the family_id column from app_user yet to ensure backward compatibility
-- ALTER TABLE app_user DROP COLUMN family_id;

COMMENT ON TABLE user_family_membership IS 'Stores the many-to-many relationship between users and families'; 