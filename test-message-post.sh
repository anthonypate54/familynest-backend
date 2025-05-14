#!/bin/bash

# Get token for authentication
TOKEN=$(curl -s -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"heavy.user@example.com","password":"user2123"}' | jq -r '.token')

echo "Obtained token: $TOKEN"

# Post a message with the standard messages endpoint
echo "Posting message with standard endpoint..."
curl -v -X POST http://localhost:8080/api/users/888/messages \
  -H "Authorization: Bearer $TOKEN" \
  -F "content=1234Open the door" \
  -F "familyId=888"

echo ""
echo "Done!" 