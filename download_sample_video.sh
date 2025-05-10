#!/bin/bash
# Script to download a sample video for testing
TIMESTAMP=$(date +%s)
VIDEO_FILENAME="${TIMESTAMP}_sample_video.mp4"
OUTPUT_PATH="uploads/$VIDEO_FILENAME"
echo "Downloading a small sample video for testing..."
echo "Output file will be: $OUTPUT_PATH"
curl -L "https://filesamples.com/samples/video/mp4/sample_640x360.mp4" -o "$OUTPUT_PATH"
