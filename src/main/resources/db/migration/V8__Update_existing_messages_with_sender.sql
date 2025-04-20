UPDATE message m
SET sender_id = (
    SELECT id 
    FROM app_user u 
    WHERE u.username = m.sender_username
)
WHERE m.sender_id IS NULL; 