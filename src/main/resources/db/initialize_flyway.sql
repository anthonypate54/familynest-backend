-- Create Flyway schema history table
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INTEGER NOT NULL PRIMARY KEY,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL
);

-- Mark existing migrations as applied
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES 
(1, '1', 'Initial schema', 'SQL', 'V1__Initial_schema.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(2, '1.1', 'Add foreign keys', 'SQL', 'V1.1__Add_foreign_keys.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(3, '2', 'add email to user', 'SQL', 'V2__add_email_to_user.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(4, '4', 'Add image to message', 'SQL', 'V4__Add_image_to_message.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(5, '5', 'Add sender id to message', 'SQL', 'V5__Add_sender_id_to_message.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(6, '6', 'Update existing messages sender id', 'SQL', 'V6__Update_existing_messages_sender_id.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(7, '7', 'Update existing messages with sender', 'SQL', 'V7__Update_existing_messages_with_sender.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(8, '8', 'Update existing messages with sender', 'SQL', 'V8__Update_existing_messages_with_sender.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(9, '9', 'Ensure users and update sender ids', 'SQL', 'V9__Ensure_users_and_update_sender_ids.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(10, '10', 'Update message sender ids', 'SQL', 'V10__Update_message_sender_ids.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(11, '11', 'Add media columns', 'SQL', 'V11__Add_media_columns.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(12, '11.1', 'Update existing messages sender ids', 'SQL', 'V11.1__Update_existing_messages_sender_ids.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(13, '12', 'Add media columns', 'SQL', 'V12__Add_media_columns.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true),
(14, '13', 'Add sender username to message', 'SQL', 'V13__Add_sender_username_to_message.sql', 0, 'postgres', CURRENT_TIMESTAMP, 0, true); 