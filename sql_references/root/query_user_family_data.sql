-- Query script to analyze user family relationships
-- Replace '1' with the target user ID where needed

-- 1. Get all families associated with user1
SELECT 
    f.id AS family_id,
    f.name AS family_name,
    ufm.role AS user_role,
    CASE WHEN f.created_by = 1 THEN 'YES' ELSE 'NO' END AS is_owner
FROM 
    family f
JOIN 
    user_family_membership ufm ON f.id = ufm.family_id
WHERE 
    ufm.user_id = 1;

-- 2. Get all direct family members for each family user1
-- belongs to (with their relationship to user1)
SELECT 
    f.id AS family_id,
    f.name AS family_name,
    u.id AS member_id,
    u.username AS member_username,
    u.first_name AS member_first_name,
    u.last_name AS member_last_name,
    ufm.role AS member_role,
    CASE WHEN f.created_by = u.id THEN 'YES' ELSE 'NO' END AS is_family_owner
FROM 
    family f
JOIN 
    user_family_membership ufm ON f.id = ufm.family_id
JOIN 
    app_user u ON ufm.user_id = u.id
WHERE 
    f.id IN (SELECT family_id FROM user_family_membership WHERE user_id = 1)
ORDER BY 
    f.id, u.id;

-- 3. Get message preferences setup for user1
-- (which members user1 has preferences for)
SELECT 
    umms.family_id,
    f.name AS family_name,
    umms.member_user_id,
    u.username AS member_username,
    u.first_name AS member_first_name, 
    u.last_name AS member_last_name,
    umms.receive_messages
FROM 
    user_member_message_settings umms
JOIN 
    app_user u ON umms.member_user_id = u.id
JOIN 
    family f ON umms.family_id = f.id
WHERE 
    umms.user_id = 1
ORDER BY 
    umms.family_id, umms.member_user_id;

-- 4. For each family member of user1, check if they own families
-- and how many members are in those families
SELECT 
    u.id AS family_member_id,
    u.username AS family_member_username,
    u.first_name AS member_first_name,
    u.last_name AS member_last_name,
    f2.id AS owned_family_id,
    f2.name AS owned_family_name,
    COUNT(ufm2.user_id) AS number_of_members
FROM 
    app_user u
JOIN 
    user_family_membership ufm ON u.id = ufm.user_id
JOIN 
    family f ON ufm.family_id = f.id
-- Find families this user is the owner of
LEFT JOIN 
    family f2 ON u.id = f2.created_by
-- Find members of those owned families
LEFT JOIN 
    user_family_membership ufm2 ON f2.id = ufm2.family_id
WHERE 
    f.id IN (SELECT family_id FROM user_family_membership WHERE user_id = 1)
    AND u.id != 1  -- Exclude user1 from results
GROUP BY 
    u.id, f2.id
ORDER BY 
    u.id, f2.id;

-- 5. Summary statistics
SELECT
    'Number of families user belongs to' AS metric,
    COUNT(DISTINCT family_id) AS value
FROM 
    user_family_membership
WHERE 
    user_id = 1
UNION ALL
SELECT
    'Number of families user owns' AS metric,
    COUNT(*) AS value
FROM 
    family
WHERE 
    created_by = 1
UNION ALL
SELECT
    'Total number of family members across all families' AS metric,
    COUNT(DISTINCT m.user_id) AS value
FROM 
    user_family_membership ufm
JOIN 
    user_family_membership m ON ufm.family_id = m.family_id
WHERE 
    ufm.user_id = 1
    AND m.user_id != 1  -- Don't count the user themselves
UNION ALL
SELECT
    'Number of members with message preferences set' AS metric,
    COUNT(DISTINCT member_user_id) AS value
FROM 
    user_member_message_settings
WHERE 
    user_id = 1; 