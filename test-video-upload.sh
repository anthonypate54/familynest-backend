#!/bin/bash

# Test script for video upload and processing endpoint

# Colors for better output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
VIDEO_FILE=""
AUTH_TOKEN=""

# Check for required curl
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed.${NC}"
    exit 1
fi

# Check if a video file is provided as an argument
if [ "$1" != "" ]; then
    VIDEO_FILE="$1"
else
    # Use the first mp4 file in the current directory
    VIDEO_FILE=$(find . -name "*.mp4" -type f | head -n 1)
    
    if [ "$VIDEO_FILE" == "" ]; then
        echo -e "${RED}Error: No video file provided and no .mp4 files found in current directory.${NC}"
        echo "Usage: $0 <video_file> [auth_token]"
        exit 1
    fi
    
    echo -e "${YELLOW}Using video file: $VIDEO_FILE${NC}"
fi

# Check if authentication token is provided
if [ "$2" != "" ]; then
    AUTH_TOKEN="$2"
    echo -e "${YELLOW}Using provided auth token${NC}"
else
    echo -e "${YELLOW}No auth token provided, endpoint might require authentication${NC}"
fi

# Base URL
BASE_URL="http://localhost:8080"
ENDPOINT="/api/videos/upload"

echo -e "${YELLOW}Uploading video to $BASE_URL$ENDPOINT${NC}"

# Construct curl command with or without auth token
if [ "$AUTH_TOKEN" != "" ]; then
    CURL_CMD="curl -v -X POST -H \"Authorization: Bearer $AUTH_TOKEN\" -F \"file=@$VIDEO_FILE\" $BASE_URL$ENDPOINT"
else
    CURL_CMD="curl -v -X POST -F \"file=@$VIDEO_FILE\" $BASE_URL$ENDPOINT"
fi

# Execute the request
echo -e "${YELLOW}Executing: $CURL_CMD${NC}"
eval $CURL_CMD

echo # Empty line
echo -e "${GREEN}Test completed.${NC}" 