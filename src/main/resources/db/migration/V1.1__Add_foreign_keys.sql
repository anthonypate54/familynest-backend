-- Add foreign key constraints
ALTER TABLE app_user ADD CONSTRAINT fk_user_family 
    FOREIGN KEY (family_id) REFERENCES family(id);

ALTER TABLE family ADD CONSTRAINT fk_family_creator 
    FOREIGN KEY (created_by) REFERENCES app_user(id);

ALTER TABLE message ADD CONSTRAINT fk_message_user 
    FOREIGN KEY (user_id) REFERENCES app_user(id);

ALTER TABLE message ADD CONSTRAINT fk_message_sender 
    FOREIGN KEY (sender_id) REFERENCES app_user(id);

ALTER TABLE invitation ADD CONSTRAINT fk_invitation_sender 
    FOREIGN KEY (sender_id) REFERENCES app_user(id);

ALTER TABLE invitation ADD CONSTRAINT fk_invitation_family 
    FOREIGN KEY (family_id) REFERENCES family(id); 