#!/bin/bash
# Script to fix the heavy user in the test database
# This addresses the schema mismatch with created_at column

echo "Adding the heavy user to the database..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test << EOF
-- Create heavy test user with proper schema (no created_at column)
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

-- Create stress test family with proper schema (no created_at column)
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
EOF

echo "Verifying heavy user creation..."
PGPASSWORD=postgres PAGER="" psql -X -U postgres -d familynest_test -c "SELECT id, username, email FROM app_user WHERE id = 888;"

echo "Heavy user fix complete."
echo "You can now log in with heavy.user@example.com and password: user2123" 