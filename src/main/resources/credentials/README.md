# Credentials Directory

This directory is for storing sensitive credential files that should **NOT** be committed to Git.

## Google Play Service Account Setup

For Google Play Real-Time Developer Notifications (RTDN) to work, you need to set up a service account:

### Development Environment

1. Create a service account in the Google Cloud Console
2. Generate a JSON key file for that service account
3. Place the JSON key file in this directory with the name `google-play-service-account.json`
4. This file will be automatically picked up by the application in development mode

### Production Environment

For production, instead of using a file:

1. Base64-encode the contents of your service account JSON file:
   ```
   base64 -i google-play-service-account.json
   ```
2. Set the encoded string as an environment variable:
   ```
   export GOOGLE_PLAY_SERVICE_ACCOUNT_CREDENTIALS="BASE64_ENCODED_JSON"
   ```
3. The application will decode this environment variable at runtime

## Security Notes

- **NEVER** commit service account keys to Git
- Keep your service account keys secure and rotate them periodically
- Restrict the permissions of your service account to only what's needed
- For production, use environment variables instead of files when possible

