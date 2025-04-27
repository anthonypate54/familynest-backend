-- Add foreign key constraints if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_user_family'
    ) THEN
        ALTER TABLE app_user ADD CONSTRAINT fk_user_family 
            FOREIGN KEY (family_id) REFERENCES family(id);
    END IF;

    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_family_creator'
    ) THEN
        ALTER TABLE family ADD CONSTRAINT fk_family_creator 
            FOREIGN KEY (created_by) REFERENCES app_user(id);
    END IF;

    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_message_user'
    ) THEN
        ALTER TABLE message ADD CONSTRAINT fk_message_user 
            FOREIGN KEY (user_id) REFERENCES app_user(id);
    END IF;

    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_message_sender'
    ) THEN
        ALTER TABLE message ADD CONSTRAINT fk_message_sender 
            FOREIGN KEY (sender_id) REFERENCES app_user(id);
    END IF;

    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_invitation_sender'
    ) THEN
        ALTER TABLE invitation ADD CONSTRAINT fk_invitation_sender 
            FOREIGN KEY (sender_id) REFERENCES app_user(id);
    END IF;

    IF NOT EXISTS (
        SELECT FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_invitation_family'
    ) THEN
        ALTER TABLE invitation ADD CONSTRAINT fk_invitation_family 
            FOREIGN KEY (family_id) REFERENCES family(id);
    END IF;
END
$$; 