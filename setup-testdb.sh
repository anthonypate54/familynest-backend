#!/bin/bash
# Test Database Setup Script for FamilyNest

echo "Setting up FamilyNest test database..."

# Disable PostgreSQL paging completely
export PAGER=""
export PGPAGER=""
export PSQL_PAGER=""

# Stop any running application using the database
echo "Stopping any processes using the database..."
# This part is commented out but can be uncommented if needed
ps aux | grep '[s]pring-boot' | grep testdb | awk '{print $2}' | xargs kill -9 2>/dev/null || true

# Terminate all connections to the test database
echo "Terminating database connections..."
psql -X -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'familynest_test';" > /dev/null 2>&1

# Drop and recreate the database
echo "Recreating the database..."
psql -X -U postgres << EOF
DROP DATABASE IF EXISTS familynest_test;
CREATE DATABASE familynest_test;
EOF

# Run the application with JPA schema generation and then shut it down
echo "Generating database schema with JPA..."
echo "This will take a few seconds..."
./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb -Dspring.jpa.hibernate.ddl-auto=create -Dspring.sql.init.mode=never &
PID=$!

# Wait for the application to start
sleep 15

# Kill the application
kill $PID
sleep 2

# Connect to the database and run the initialization scripts
echo "Populating test data..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -f src/main/resources/data-testdb.sql

# Create test users with FIXED password hashes for consistent testing
echo "Creating test users with consistent password hashes..."

# IMPORTANT: We use fixed BCrypt hashes for testing to ensure passwords are always the same
# The hash '$2a$10$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.' is for 'user2123'
# Using fixed hashes prevents issues with BCrypt generating different hashes each time

# Create/update the admin test user with known credentials
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test << EOF
-- Update or insert the admin user
INSERT INTO app_user (id, username, email, password, first_name, last_name, role, photo)
VALUES (
  101, 
  'john.doe', 
  'john.doe@example.com', 
  '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.', 
  'John', 
  'Doe', 
  'ADMIN',
  NULL
) 
ON CONFLICT (id) DO UPDATE SET 
  password = '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.',
  username = 'john.doe',
  email = 'john.doe@example.com',
  role = 'ADMIN';
EOF

# Create a special user with password "user2123" for easy testing
echo "Creating a special test user with known credentials..."

# Using proper password escaping for PostgreSQL
# Password hash for 'user2123' - Using the known fixed hash
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test << EOF
INSERT INTO app_user (id, username, email, password, first_name, last_name, role, photo)
VALUES (
  999, 
  'testuser', 
  'testuser@example.com', 
  '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.', 
  'Test', 
  'User', 
  'USER',
  NULL
) 
ON CONFLICT (id) DO UPDATE SET 
  password = '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.',
  username = 'testuser',
  email = 'testuser@example.com';
EOF

# Create heavy test user with 5000 messages for stress testing
echo "Creating heavy test user with 5000 messages (this may take a minute)..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test << EOF
-- Create heavy test user
INSERT INTO app_user (id, username, email, password, first_name, last_name, role, photo)
VALUES (
  888, 
  'heavy.user', 
  'heavy.user@example.com', 
  '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.', 
  'Heavy', 
  'User', 
  'USER',
  NULL
) 
ON CONFLICT (id) DO UPDATE SET 
  password = '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.',
  username = 'heavy.user',
  email = 'heavy.user@example.com';

-- Create stress test family
INSERT INTO family (id, name, created_by) 
VALUES (888, 'Stress Test Family', 888)
ON CONFLICT (id) DO NOTHING;

-- Add heavy user to family
INSERT INTO user_family_membership (family_id, user_id, role, is_active)
SELECT 888, 888, 'ADMIN', true
WHERE NOT EXISTS (
  SELECT 1 FROM user_family_membership 
  WHERE user_id = 888 AND family_id = 888
);

-- Add other test users to stress test family
INSERT INTO user_family_membership (family_id, user_id, role, is_active)
SELECT 888, 101, 'MEMBER', true
WHERE NOT EXISTS (
  SELECT 1 FROM user_family_membership 
  WHERE user_id = 101 AND family_id = 888
);

INSERT INTO user_family_membership (family_id, user_id, role, is_active)
SELECT 888, 999, 'MEMBER', true
WHERE NOT EXISTS (
  SELECT 1 FROM user_family_membership 
  WHERE user_id = 999 AND family_id = 888
);

-- Create message script for generating 5000 messages
DO \$\$
DECLARE
  i INTEGER;
  thread_id INTEGER := 1;
  total_messages INTEGER := 5000;
  threads_count INTEGER := 50;
  replies_per_thread INTEGER;
  current_message_id INTEGER;
  base_message_id INTEGER;
BEGIN
  -- Calculate replies per thread
  replies_per_thread := (total_messages / threads_count) - 1;
  
  -- Get current max message id
  SELECT COALESCE(MAX(id), 1000) INTO current_message_id FROM message;
  base_message_id := current_message_id;
  
  -- Generate thread starters
  FOR thread_id IN 1..threads_count LOOP
    current_message_id := current_message_id + 1;
    
    -- Insert thread starter message
    INSERT INTO message (
      id, content, sender_username, sender_id, family_id, timestamp, 
      media_type, media_url, user_id
    ) VALUES (
      current_message_id,
      'Stress test thread #' || thread_id || ' - This is a thread starter to test performance with many messages and replies',
      'heavy.user',
      888,
      888,
      NOW() - ((threads_count - thread_id) || ' hours')::INTERVAL,
      NULL,
      NULL,
      888
    );
    
    -- Generate replies for this thread
    FOR i IN 1..replies_per_thread LOOP
      current_message_id := current_message_id + 1;
      
      -- Insert reply message
      INSERT INTO message (
        id, content, sender_username, sender_id, family_id, timestamp, 
        media_type, media_url, user_id
      ) VALUES (
        current_message_id,
        'Reply #' || i || ' to thread #' || thread_id || ' - Testing performance with many messages and nested replies',
        'heavy.user',
        888,
        888,
        NOW() - ((threads_count - thread_id) || ' hours')::INTERVAL + (i || ' minutes')::INTERVAL,
        NULL,
        NULL,
        888
      );
      
      -- Add some engagement (reactions, comments)
      -- Removed message_view table - no longer tracking views
      
      -- Reactions
      IF i % 5 = 0 THEN
        INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
        VALUES (current_message_id, 101, 'LIKE', NOW());
      END IF;
      IF i % 7 = 0 THEN
        INSERT INTO message_reaction (message_id, user_id, reaction_type, created_at)
        VALUES (current_message_id, 999, 'HEART', NOW());
      END IF;
      
      -- Comments
      IF i % 10 = 0 THEN
        INSERT INTO message_comment (parent_message_id, user_id, content, timestamp)
        VALUES (current_message_id, 101, 'Test comment on message ' || current_message_id, NOW());
      END IF;
      IF i % 13 = 0 THEN
        INSERT INTO message_comment (parent_message_id, user_id, content, timestamp)
        VALUES (current_message_id, 999, 'Another test comment', NOW());
      END IF;
    END LOOP;
  END LOOP;
  
  -- Report how many messages were created
  RAISE NOTICE 'Created % messages across % threads for stress testing', (current_message_id - base_message_id), threads_count;
END \$\$;
EOF

echo "Creating basic family membership for test user..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test << EOF
-- First create a family if needed
INSERT INTO family (id, name, created_by) 
VALUES (201, 'Test Family', 101)
ON CONFLICT (id) DO NOTHING;

-- Then create the membership
INSERT INTO user_family_membership (family_id, user_id, role, is_active)
SELECT 201, 999, 'MEMBER', true
WHERE NOT EXISTS (
  SELECT 1 FROM user_family_membership 
  WHERE user_id = 999 AND family_id = 201
);

-- Also ensure john.doe has a family membership
INSERT INTO user_family_membership (family_id, user_id, role, is_active)
SELECT 201, 101, 'ADMIN', true
WHERE NOT EXISTS (
  SELECT 1 FROM user_family_membership 
  WHERE user_id = 101 AND family_id = 201
);
EOF

# Update ALL users to have the same password for testing
echo "Setting all users to have the same test password..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -c "
UPDATE app_user SET password = '\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.';
"

# Display information about the large thread with clear formatting
echo ""
echo "======================= THREAD INFORMATION ======================="
echo "1. THREAD STARTER:"

# Use the -t flag (tuples only) to avoid displaying headers
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Thread ID: ' || id || ', Family: ' || family_id || ', Sender: ' || sender_username || ' (ID: ' || sender_id || ')' FROM message WHERE content LIKE 'This is the start of our load testing thread%';"
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Content: ' || content FROM message WHERE content LIKE 'This is the start of our load testing thread%';"

echo ""
echo "2. MESSAGE DISTRIBUTION BY USER:"

# Format the output for better visibility without tables
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  ' || sender_username || ': ' || message_count || ' messages' FROM (SELECT sender_username, COUNT(*) as message_count FROM message WHERE family_id = (SELECT family_id FROM message WHERE content LIKE 'This is the start of our load testing thread%') GROUP BY sender_username ORDER BY message_count DESC) as counts;"

echo "=================================================================="

# Display information about the stress test user
echo ""
echo "======================= STRESS TEST USER INFO ===================="
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Total Messages: ' || COUNT(*) FROM message WHERE sender_id = 888;"
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Thread Count: ' || COUNT(*) FROM (SELECT DISTINCT family_id FROM message WHERE sender_id = 888 AND content LIKE 'Stress test thread%') as t;"
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Engagement - Views: Removed (no longer tracking)';"
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Engagement - Reactions: ' || COUNT(*) FROM message_reaction mr JOIN message m ON mr.message_id = m.id WHERE m.sender_id = 888;"
PGPASSWORD=postgres PAGER="" psql -X -t -U postgres -d familynest_test -c "SELECT '  Engagement - Comments: ' || COUNT(*) FROM message_comment mc JOIN message m ON mc.parent_message_id = m.id WHERE m.sender_id = 888;"
echo "=================================================================="

# Apply performance indexes immediately after database creation
echo "Applying performance indexes to the database..."
PGPASSWORD=postgres psql -X -U postgres -d familynest_test -f src/main/resources/performance_indexes.sql

# Fix message sequence value to ensure it's higher than any existing message ID
echo "Fixing message sequence value to prevent duplicate key violations..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -c "
SELECT setval('message_id_seq', COALESCE((SELECT MAX(id) FROM message), 0) + 100, true);
"

# List available test users
echo ""
echo "Available test users (all have the same password):"
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -c "SELECT id, username, email, role FROM app_user WHERE id IN (101, 999, 888) OR username IN ('jane.doe', 'bob.smith', 'alice.johnson');"

echo ""
echo "Database setup complete!"
echo "To run the application with the test database:"
echo "./mvnw spring-boot:run -Dspring-boot.run.profiles=testdb"
echo ""
echo "To test the app with the Flutter client, use ANY user email with password: user2123"
echo "Example accounts:"
echo "  - john.doe@example.com  (Admin)"
echo "  - testuser@example.com  (Regular User)"
echo "  - heavy.user@example.com (Stress Test User with 5000 messages)"
echo ""
echo "To test metrics data, use the pre-configured JWT token:"
echo "curl -H \"Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMDEiLCJyb2xlIjoiVVNFUiIsImV4cCI6MTc0NjY1MDI2OX0.S-kbcG3qS6C8UgiN4GVLtjwbloHZda5c5qfG76WBCqnOWKEnowA47grgz9YaIKn0H0FLzbboB4CAm4GIwbgjXw\" \\"
echo "  http://localhost:8080/api/messages/102/engagement"
echo ""
echo "Or query directly from the database:"
echo "psql -X -U postgres -d familynest_test -c \"SELECT COUNT(*) FROM user_message_read WHERE message_id = 102;\""
echo ""
echo "To test with the heavy user (5000 messages):"
echo "psql -X -U postgres -d familynest_test -c \"SELECT COUNT(*) FROM message WHERE sender_id = 888;\"" 