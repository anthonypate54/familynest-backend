-- First ensure all users exist
INSERT INTO app_user (username, email, password, first_name, last_name, role)
SELECT DISTINCT 
    m.sender_username,
    CONCAT(m.sender_username, '@example.com'),
    '$2a$10$default_password_hash',
    m.sender_username,
    '',
    'USER'
FROM message m
WHERE m.sender_username NOT IN (SELECT username FROM app_user)
AND m.sender_username IS NOT NULL;

-- Then update sender_ids
UPDATE message m
SET sender_id = (
    SELECT id 
    FROM app_user u 
    WHERE u.username = m.sender_username
)
WHERE m.sender_id IS NULL
AND m.sender_username IS NOT NULL; 