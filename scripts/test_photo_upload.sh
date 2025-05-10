#!/bin/bash
# Script to test photo upload and retrieval

echo "Creating test upload directory"
mkdir -p /tmp/familynest-uploads
chmod 755 /tmp/familynest-uploads

# Create a simple test image if it doesn't exist
if [ ! -f /tmp/test_photo.jpg ]; then
  echo "Creating test image"
  # Create a small text file that we'll use as our "image"
  cat > /tmp/test_photo.jpg << EOF
This is a test file that will serve as our mock image.
It's not actually a valid JPG, but it will work for our testing purposes.
The server will still attempt to serve this as a file.
EOF
  echo "Created a simple test file at /tmp/test_photo.jpg"
fi

# Check if server is running
echo "Checking if server is running"
curl -s http://localhost:8080/api/test/photo-test > /dev/null
if [ $? -ne 0 ]; then
  echo "Error: Server is not running at localhost:8080"
  echo "Please start the server first"
  exit 1
fi

# Test the photo-test endpoint
echo "Testing photo endpoint"
PHOTO_TEST_RESPONSE=$(curl -s http://localhost:8080/api/test/photo-test)
echo "Photo test response: $PHOTO_TEST_RESPONSE"

# Upload a test photo
echo "Uploading test photo"
UPLOAD_RESPONSE=$(curl -s -X POST \
  -F "photo=@/tmp/test_photo.jpg" \
  http://localhost:8080/api/test/upload-test)

echo "Upload response: $UPLOAD_RESPONSE"

# Extract the photo URL from the response
PHOTO_URL=$(echo $UPLOAD_RESPONSE | grep -o '"/api/users/photos/[^"]*"' | tr -d '"')

if [ -z "$PHOTO_URL" ]; then
  echo "Error: Could not extract photo URL from response"
  exit 1
fi

echo "Photo URL: $PHOTO_URL"

# Try to retrieve the photo
echo "Attempting to retrieve the uploaded photo"
RETRIEVAL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080$PHOTO_URL)

if [ "$RETRIEVAL_STATUS" == "200" ]; then
  echo "Success! Photo retrieved successfully."
  # Save the photo for verification
  curl -s http://localhost:8080$PHOTO_URL > /tmp/retrieved_photo.jpg
  echo "The retrieved photo was saved to /tmp/retrieved_photo.jpg"
  
  # Check if the retrieved file is the same as the original
  if cmp -s /tmp/test_photo.jpg /tmp/retrieved_photo.jpg; then
    echo "Verification successful: The retrieved photo is identical to the uploaded photo."
  else
    echo "Warning: The retrieved photo differs from the uploaded photo."
    echo "This may be due to processing on the server."
  fi
else
  echo "Error: Failed to retrieve the photo. HTTP status: $RETRIEVAL_STATUS"
  echo "This suggests the servePhoto endpoint is not functioning correctly."
fi

echo "Test completed." 