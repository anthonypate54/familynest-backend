#!/bin/bash
# Performance test script for FamilyNest application
# This script sends requests to test API endpoints and measures response times

echo "Running performance tests for FamilyNest API..."

# Function to measure API response time
measure_endpoint() {
  local endpoint=$1
  local method=$2
  local description=$3
  local data=$4
  local token=$5
  
  echo "Testing $description..."
  if [ "$method" == "GET" ]; then
    # GET request
    time curl -s -X GET \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      "http://localhost:8080$endpoint" > /dev/null
  else
    # POST request with data
    time curl -s -X POST \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      -d "$data" \
      "http://localhost:8080$endpoint" > /dev/null
  fi
  echo ""
}

# Get an auth token
echo "Getting authentication token..."
TOKEN_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' \
  http://localhost:8080/api/users/login)
TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
USER_ID=$(echo $TOKEN_RESPONSE | grep -o '"userId":[0-9]*' | cut -d':' -f2)

# If token is empty, try with the test user
if [ -z "$TOKEN" ]; then
  echo "Using default test user..."
  TOKEN_RESPONSE=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{"email":"john.doe@example.com","password":"user2123"}' \
    http://localhost:8080/api/users/login)
  TOKEN=$(echo $TOKEN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
  USER_ID=$(echo $TOKEN_RESPONSE | grep -o '"userId":[0-9]*' | cut -d':' -f2)
fi

echo "Using user ID: $USER_ID with token: $TOKEN"

# Standard Tests (using normal user)
echo "============================================================"
echo "STANDARD TESTS (Normal User)"
echo "============================================================"

# Test 1: Get messages for user (should use optimized query)
measure_endpoint "/api/messages/user/$USER_ID?page=0&size=20" "GET" "Get messages for user" "" "$TOKEN"

# Test 2: Get user profile (should use optimized user query)
measure_endpoint "/api/users/$USER_ID" "GET" "Get user profile" "" "$TOKEN"

# Test 3: Get user messages (should use optimized batch loading)
measure_endpoint "/api/users/$USER_ID/messages" "GET" "Get user messages" "" "$TOKEN"

# Test 4: Get family members (should use optimized queries)
measure_endpoint "/api/users/$USER_ID/family-members" "GET" "Get family members" "" "$TOKEN"

# Stress Tests with Heavy User
echo "============================================================"
echo "STRESS TESTS (Heavy User with 5000 messages)"
echo "============================================================"

# Login as heavy user
echo "Logging in as heavy.user@example.com..."
HEAVY_TOKEN_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"heavy.user@example.com","password":"user2123"}' \
  http://localhost:8080/api/users/login)
HEAVY_TOKEN=$(echo $HEAVY_TOKEN_RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
HEAVY_USER_ID=$(echo $HEAVY_TOKEN_RESPONSE | grep -o '"userId":[0-9]*' | cut -d':' -f2)

echo "Using heavy user ID: $HEAVY_USER_ID"

# Test 1: Get first page of messages (20 messages)
measure_endpoint "/api/messages/user/$HEAVY_USER_ID?page=0&size=20" "GET" "Get first page of messages (20)" "" "$HEAVY_TOKEN"

# Test 2: Get larger page of messages (100 messages)
measure_endpoint "/api/messages/user/$HEAVY_USER_ID?page=0&size=100" "GET" "Get larger page of messages (100)" "" "$HEAVY_TOKEN"

# Test 3: Get very large page of messages (500 messages)
measure_endpoint "/api/messages/user/$HEAVY_USER_ID?page=0&size=500" "GET" "Get very large page of messages (500)" "" "$HEAVY_TOKEN"

# Test 4: Get user profile with all the engagement data
measure_endpoint "/api/users/$HEAVY_USER_ID" "GET" "Get heavy user profile with engagement data" "" "$HEAVY_TOKEN"

# Test 5: Get later pages of messages to test offset performance
measure_endpoint "/api/messages/user/$HEAVY_USER_ID?page=5&size=100" "GET" "Get messages with offset (page 5, size 100)" "" "$HEAVY_TOKEN"

# Test 6: Get user messages with batch loading engagement data
measure_endpoint "/api/users/$HEAVY_USER_ID/messages" "GET" "Get all user messages with batch loading" "" "$HEAVY_TOKEN"

# Verify indexes are present
echo "============================================================"
echo "DATABASE ANALYSIS"
echo "============================================================"

echo "Verifying database indexes..."
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "\di" | grep -E 'idx_message_|idx_user_|idx_family_'

# Verify performance stats
echo "Database query statistics (showing most frequent queries):"
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "SELECT query, calls, total_time, mean_time FROM pg_stat_statements ORDER BY total_time DESC LIMIT 10;" 2>/dev/null || echo "pg_stat_statements extension not available"

# Count total messages in database
echo "Total messages in database:"
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "SELECT COUNT(*) FROM message;"

# Count heavy user messages
echo "Total messages by heavy user:"
PGPASSWORD=postgres psql -U postgres -d familynest_test -c "SELECT COUNT(*) FROM message WHERE sender_id = 888;"

echo "Performance tests completed!" 