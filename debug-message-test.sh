#!/bin/bash

# Get token for authentication
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"heavy.user@example.com","password":"user2123"}' | jq -r '.token')

# Display some debugging info
echo "============= ACCOUNT INFO ============="
echo "Using token for user heavy.user (id=888)"
echo "========================================="

# Check user info first to verify account state
echo "Retrieving user information..."
USER_INFO=$(curl -s -X GET http://localhost:8080/api/users/888 \
  -H "Authorization: Bearer $TOKEN")
  
echo "$USER_INFO" | jq .

# Get active family
echo "Checking active family..."
FAMILY_INFO=$(curl -s -X GET http://localhost:8080/api/users/888/active-family \
  -H "Authorization: Bearer $TOKEN")
  
echo "$FAMILY_INFO" | jq .

# Get all families
echo "Listing all user families..."
FAMILIES=$(curl -s -X GET http://localhost:8080/api/users/888/families \
  -H "Authorization: Bearer $TOKEN")
  
echo "$FAMILIES" | jq .

# Post message
echo "============= POSTING MESSAGE ============="
echo "Posting message with content '1234Open the door' to family ID 888..."
RESPONSE=$(curl -v -X POST http://localhost:8080/api/users/888/messages \
  -H "Authorization: Bearer $TOKEN" \
  -F "content=1234Open the door test 2" \
  -F "familyId=888" 2>&1)
  
echo "$RESPONSE"

# Verify posted message
echo "============= VERIFYING DATABASE ============="
echo "Checking for recently posted message in database..."
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "SELECT * FROM message WHERE content LIKE '%1234Open the door%' ORDER BY timestamp DESC LIMIT 5;"

echo "Done!" 