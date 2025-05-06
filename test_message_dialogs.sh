#!/bin/bash

# Test script for the message dialog endpoints

BACKEND_URL="http://localhost:8080"
TOKEN="test_token"  # Use a fixed test token for testing
USER_ID="1"         # Use a fixed test user ID

# Function to log in and get a token (only for reference, not used in test mode)
login() {
  echo "Using test token for authentication..."
  echo "Using user ID: $USER_ID"
}

# Test 1: Get message preferences (used by FamiliesMessageDialog)
test_get_message_preferences() {
  echo -e "\n--- Test 1: Get Message Preferences ---"
  response=$(curl -s -X GET "$BACKEND_URL/api/message-preferences/$USER_ID" \
    -H "Authorization: Bearer $TOKEN")
  
  echo "Response:"
  echo $response | jq '.'
  
  # Check if response contains "familyId" and "receiveMessages" fields
  if echo $response | jq -e 'type == "array"' >/dev/null; then
    echo "✅ Test 1 passed: Response is an array (expected format)"
    
    if echo $response | jq -e 'length > 0 and .[0] | has("familyId") and has("receiveMessages")' >/dev/null; then
      echo "  ✓ Response contains expected fields"
    else
      echo "  ℹ️ Array is empty or missing expected fields (might be expected for new users)"
    fi
  else
    echo "❌ Test 1 failed: Response is not an array"
  fi
}

# Test 2: Update message preference (used by FamiliesMessageDialog)
test_update_message_preference() {
  echo -e "\n--- Test 2: Update Message Preference ---"
  
  # Use a fixed family ID for testing
  family_id="1"
  
  echo "Using family ID: $family_id"
  
  response=$(curl -s -X POST "$BACKEND_URL/api/message-preferences/$USER_ID/update" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"familyId\":$family_id,\"receiveMessages\":true}")
  
  echo "Response:"
  echo $response | jq '.'
  
  # Check if response contains success fields
  if echo $response | jq -e 'has("familyId") and has("receiveMessages")' >/dev/null; then
    echo "✅ Test 2 passed: Message preference updated successfully"
  else
    echo "❌ Test 2 failed: Failed to update message preference"
  fi
}

# Test 3: Get member message preferences (used by MemberMessageDialog)
test_get_member_message_preferences() {
  echo -e "\n--- Test 3: Get Member Message Preferences ---"
  response=$(curl -s -X GET "$BACKEND_URL/api/member-message-preferences/$USER_ID" \
    -H "Authorization: Bearer $TOKEN")
  
  echo "Response:"
  echo $response | jq '.'
  
  # Check if response is an array (even if empty)
  if echo $response | jq -e 'type == "array"' >/dev/null; then
    echo "✅ Test 3 passed: Received an array response"
    
    # If the array has members, check for expected fields
    if echo $response | jq -e 'length > 0' >/dev/null; then
      if echo $response | jq -e '.[0] | has("memberUserId") and has("receiveMessages")' >/dev/null; then
        echo "  ✓ Response contains member preferences with expected fields"
      else
        echo "  ⚠️ Response array exists but doesn't have expected structure"
      fi
    else
      echo "  ℹ️ No member preferences found (this might be expected if the user has no family members)"
    fi
  else
    echo "❌ Test 3 failed: Response is not an array"
  fi
}

# Test 4: Update member message preference (used by MemberMessageDialog)
test_update_member_message_preference() {
  echo -e "\n--- Test 4: Update Member Message Preference ---"
  
  # Use fixed IDs for testing
  family_id="1"
  member_id="2"  # Use another user as the member
  
  echo "Using family ID: $family_id, member ID: $member_id"
  
  response=$(curl -s -X POST "$BACKEND_URL/api/member-message-preferences/$USER_ID/update" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"familyId\":$family_id,\"memberUserId\":$member_id,\"receiveMessages\":true}")
  
  echo "Response:"
  echo $response | jq '.'
  
  # Check if response contains success fields
  if echo $response | jq -e 'has("familyId") and has("memberUserId") and has("receiveMessages")' >/dev/null; then
    echo "✅ Test 4 passed: Member message preference updated successfully"
  else
    echo "❌ Test 4 failed: Failed to update member message preference"
  fi
}

# Test 5: Get family members by family ID (used by MemberMessageDialog)
test_get_family_members() {
  echo -e "\n--- Test 5: Get Family Members By Family ID ---"
  
  # Use a fixed family ID for testing
  family_id="1"
  
  echo "Using family ID: $family_id"
  
  # This is the endpoint used internally by getFamilyMembersByFamilyId 
  response=$(curl -s -X GET "$BACKEND_URL/api/users/$USER_ID/family-members-by-family/$family_id" \
    -H "Authorization: Bearer $TOKEN")
  
  echo "Response:"
  echo $response | jq '.'
  
  # Check if response is an array (even if empty)
  if echo $response | jq -e 'type == "array"' >/dev/null; then
    echo "✅ Test 5 passed: Received family members array"
    
    # If the array has members, check for expected fields
    if echo $response | jq -e 'length > 0' >/dev/null; then
      if echo $response | jq -e '.[0] | has("memberUserId") or has("userId")' >/dev/null; then
        echo "  ✓ Response contains family members with expected fields"
      else
        echo "  ⚠️ Response array exists but doesn't have expected structure"
      fi
    else
      echo "  ℹ️ No family members found (this might be expected for a new family)"
    fi
  else
    echo "❌ Test 5 failed: Response is not an array"
  fi
}

# Run all tests
main() {
  echo "=== Message Dialog API Test ==="
  echo "Testing against: $BACKEND_URL"
  echo "Using test user ID: $USER_ID"
  echo "Using test auth token"
  
  login
  
  test_get_message_preferences
  test_update_message_preference
  test_get_member_message_preferences
  test_update_member_message_preference
  test_get_family_members
  
  echo -e "\n=== Test Summary ==="
  echo "All tests completed. Check the results above for any failures."
}

# Run the main function
main 