CREATE TABLE user_family_message_settings (
    user_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    receive_messages BOOLEAN DEFAULT TRUE NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, family_id),
    CONSTRAINT fk_message_settings_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_message_settings_family FOREIGN KEY (family_id) REFERENCES family(id)
);

-- Add indexes for faster lookup
CREATE INDEX idx_message_settings_user ON user_family_message_settings(user_id);
CREATE INDEX idx_message_settings_family ON user_family_message_settings(family_id);

-- Create entries for existing family memberships
INSERT INTO user_family_message_settings (user_id, family_id, receive_messages)
SELECT u.user_id, u.family_id, TRUE
FROM user_family_membership u; 