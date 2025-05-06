#!/bin/bash

# Check API relationships directly by calling the endpoints
# This verifies that the API endpoints are working correctly

# Configurable settings
API_URL=${1:-"http://localhost:8080"}  # First argument or default
USER_ID=${2:-1}  # Second argument or default
# Use X-Test-User-Id header for test authentication (allows us to bypass the token)
TEST_AUTH_HEADER="X-Test-User-Id: $USER_ID"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# Function to run API request and validate response
check_endpoint() {
    local endpoint="$1"
    local description="$2"
    local expected_type="$3"  # array or object
    
    echo -e "${YELLOW}=== Checking: $description ===${NC}"
    echo "Endpoint: $endpoint"
    
    # Make the request
    response=$(curl -s -H "$TEST_AUTH_HEADER" "$API_URL$endpoint")
    
    # Check for error responses
    if [[ "$response" == *"error"* ]]; then
        echo -e "${RED}ERROR: API returned an error${NC}"
        echo "$response" | jq '.'
        return 1
    fi
    
    # Validate response type
    if [[ "$expected_type" == "array" ]]; then
        if echo "$response" | jq -e 'if type=="array" then true else false end' > /dev/null; then
            item_count=$(echo "$response" | jq 'length')
            echo -e "${GREEN}Success: Response is an array with $item_count items${NC}"
            
            # Show sample of array (first item)
            if [[ $item_count -gt 0 ]]; then
                echo "Sample item (first in array):"
                echo "$response" | jq '.[0]'
            else
                echo "Array is empty"
            fi
        else
            echo -e "${RED}ERROR: Expected array but got different type${NC}"
            echo "$response" | jq '.'
            return 1
        fi
    elif [[ "$expected_type" == "object" ]]; then
        if echo "$response" | jq -e 'if type=="object" then true else false end' > /dev/null; then
            echo -e "${GREEN}Success: Response is an object${NC}"
            echo "$response" | jq '.'
        else
            echo -e "${RED}ERROR: Expected object but got different type${NC}"
            echo "$response" | jq '.'
            return 1
        fi
    fi
    
    echo ""
    return 0
}

echo "=== API Relationship Test ==="
echo "API URL: $API_URL"
echo "User ID: $USER_ID"
echo "=========================="
echo ""

# Check user details
check_endpoint "/api/users/$USER_ID" "User Details" "object"

# Check family-level message preferences
check_endpoint "/api/message-preferences/$USER_ID" "Family Message Preferences" "array"

# Check member-level message preferences
check_endpoint "/api/member-message-preferences/$USER_ID" "Member Message Preferences" "array"

# Check user's family memberships
check_endpoint "/api/users/$USER_ID/families" "User's Families" "array"

# Let's try a more complex operation - first get a family ID the user belongs to
family_response=$(curl -s -H "$TEST_AUTH_HEADER" "$API_URL/api/users/$USER_ID/families")
if [[ $(echo "$family_response" | jq 'length') -gt 0 ]]; then
    family_id=$(echo "$family_response" | jq -r '.[0].familyId')
    echo "Found user's family ID: $family_id"
    
    # Now get members of this family
    check_endpoint "/api/users/$USER_ID/family-members-by-family/$family_id" "Family Members by Family ID" "array"
    
    # Check the responses we would use for the MemberMessageDialog
    echo -e "${YELLOW}=== Checking Member Dialog API Sequence ===${NC}"
    echo "This tests the sequence of API calls the MemberMessageDialog would make"
    echo ""
    
    # 1. Get the user's families from the message preferences endpoint
    family_prefs=$(curl -s -H "$TEST_AUTH_HEADER" "$API_URL/api/message-preferences/$USER_ID")
    family_count=$(echo "$family_prefs" | jq 'length')
    echo "1. Found $family_count families in message preferences"
    
    if [[ $family_count -gt 0 ]]; then
        # 2. Get the family members for the first family
        first_family_id=$(echo "$family_prefs" | jq -r '.[0].familyId')
        echo "2. Using first family ID: $first_family_id"
        
        members_response=$(curl -s -H "$TEST_AUTH_HEADER" "$API_URL/api/users/$USER_ID/family-members-by-family/$first_family_id")
        members_count=$(echo "$members_response" | jq 'length')
        echo "3. Found $members_count members in family $first_family_id"
        
        if [[ $members_count -gt 0 ]]; then
            # 3. Get the member message preferences for these family members
            member_prefs=$(curl -s -H "$TEST_AUTH_HEADER" "$API_URL/api/member-message-preferences/$USER_ID")
            pref_count=$(echo "$member_prefs" | jq 'length')
            echo "4. Found $pref_count member message preferences"
            
            # 4. Try to update a preference for one member
            if [[ $members_count -gt 1 ]]; then
                # Get the ID of a member that isn't the user
                member_id=$(echo "$members_response" | jq -r --arg uid "$USER_ID" 'map(select(.userId != ($uid | tonumber))) | .[0].userId')
                
                if [[ -n "$member_id" && "$member_id" != "null" ]]; then
                    echo "5. Selected member ID $member_id for preference update test"
                    
                    # Make the update request with new preference
                    update_data="{\"familyId\":$first_family_id,\"memberUserId\":$member_id,\"receiveMessages\":true}"
                    update_url="/api/member-message-preferences/$USER_ID/update"
                    
                    echo "Making update request to $update_url with data: $update_data"
                    update_response=$(curl -s -X POST -H "$TEST_AUTH_HEADER" -H "Content-Type: application/json" \
                        -d "$update_data" "$API_URL$update_url")
                    
                    # Check if update was successful
                    if echo "$update_response" | jq -e '.memberUserId == '"$member_id"'' > /dev/null; then
                        echo -e "${GREEN}6. Successfully updated preference for member $member_id${NC}"
                        echo "$update_response" | jq '.'
                    else
                        echo -e "${RED}6. Failed to update preference for member $member_id${NC}"
                        echo "$update_response" | jq '.'
                    fi
                fi
            fi
        fi
    fi
fi 