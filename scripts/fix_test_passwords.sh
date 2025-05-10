#!/bin/bash
# Script to fix test user passwords in the FamilyNest database
# Use this when you need to reset passwords without rebuilding the entire database

# Define constants
KNOWN_HASH='\$2a\$10\$2ZXfPESR9fSQb1pi7UJNz.Cx88HkqOX3MfZfqjxs1o81Azg9VYPE.'
TEST_PASSWORD='user2123'

echo "Fixing all user passwords in familynest_test database..."

# Set all users to have the same password hash
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "
UPDATE app_user SET password = '$KNOWN_HASH';
"

echo "Listing available test users:"
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "SELECT id, username, email FROM app_user LIMIT 10;"

echo ""
echo "Fixed passwords for ALL users."
echo "You can now login with any user email using password: $TEST_PASSWORD"
echo ""
echo "Example test accounts:"
echo "  - john.doe@example.com  (Admin)"
echo "  - testuser@example.com  (Regular User)"
echo ""
echo "All passwords have been set to: $TEST_PASSWORD" 