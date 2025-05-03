-- Create member level message preferences table
CREATE TABLE user_member_message_settings (
    user_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    member_user_id BIGINT NOT NULL,
    receive_messages BOOLEAN NOT NULL DEFAULT TRUE,
    last_updated TIMESTAMP,
    PRIMARY KEY (user_id, family_id, member_user_id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (family_id) REFERENCES family(id),
    FOREIGN KEY (member_user_id) REFERENCES app_user(id)
);

-- Add indexes for better performance
CREATE INDEX idx_user_member_message_settings_user_id ON user_member_message_settings(user_id);
CREATE INDEX idx_user_member_message_settings_family_id ON user_member_message_settings(family_id);
CREATE INDEX idx_user_member_message_settings_member_user_id ON user_member_message_settings(member_user_id);

-- Add comments
COMMENT ON TABLE user_member_message_settings IS 'Stores user preferences for receiving messages from specific family members';
COMMENT ON COLUMN user_member_message_settings.user_id IS 'The user ID of the preference owner';
COMMENT ON COLUMN user_member_message_settings.family_id IS 'The family ID for this preference';
COMMENT ON COLUMN user_member_message_settings.member_user_id IS 'The user ID of the family member this preference applies to';
COMMENT ON COLUMN user_member_message_settings.receive_messages IS 'Whether to receive messages from this family member (true) or not (false)';
COMMENT ON COLUMN user_member_message_settings.last_updated IS 'When this preference was last updated'; 