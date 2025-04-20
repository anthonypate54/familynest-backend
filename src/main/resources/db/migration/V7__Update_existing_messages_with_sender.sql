-- First, ensure all users exist for the sender_usernames
INSERT INTO app_user (username, email, password, role)
SELECT DISTINCT 
    m.sender_username,
    CONCAT(m.sender_username, '@example.com'),
    'placeholder',
    'USER'
FROM message m
WHERE NOT EXISTS (
    SELECT 1 FROM app_user u WHERE u.username = m.sender_username
);

-- Then update the sender_id for all messages
UPDATE message m
SET sender_id = (
    SELECT id 
    FROM app_user u 
    WHERE u.username = m.sender_username
)
WHERE m.sender_id IS NULL; 