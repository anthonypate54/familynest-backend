# Firebase Push Notifications Setup

## Overview

The FamilyNest backend uses Firebase Admin SDK to send push notifications to mobile devices when:
- Family messages are posted
- DM messages are sent
- Comments are added to messages

## Setup Instructions

### 1. Firebase Service Account

To enable push notifications, you need to create a Firebase service account:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `familynest-notifications`
3. Go to **Project Settings** → **Service Accounts**
4. Click **Generate New Private Key**
5. Download the JSON file
6. Rename it to `firebase-service-account.json`
7. Place it in `src/main/resources/`

### 2. File Structure

```
src/main/resources/
├── firebase-service-account.json          # Your actual service account (do not commit!)
├── firebase-service-account.json.example  # Example file showing structure
└── application.properties
```

### 3. Security Notes

- **Never commit** the actual `firebase-service-account.json` file to version control
- The file is already added to `.gitignore`
- For production, use environment variables or cloud secret management

### 4. Environment Variables (Production)

For production deployment, set these environment variables instead:

```bash
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
# OR use Google Cloud default credentials
```

### 5. Testing Without Firebase

If you don't have Firebase configured, the application will:
- Start normally without errors
- Log warnings about missing Firebase configuration
- Skip sending push notifications (WebSocket notifications still work)

## How It Works

1. **FCM Token Registration**: When users log in, their FCM token is sent to `/api/users/{userId}/fcm-token`
2. **Notification Preferences**: Push notifications respect user settings in `user_notification_settings` table
3. **Background Notifications**: Push notifications are sent when the app is closed/backgrounded
4. **Real-time Notifications**: WebSocket notifications handle real-time updates when app is open

## Troubleshooting

### "Firebase not initialized" warnings
- Make sure `firebase-service-account.json` exists in `src/main/resources/`
- Check that the JSON file is valid and has correct project_id

### Push notifications not working
1. Check that user has `device_permission_granted = TRUE` in database
2. Verify user has `push_notifications_enabled = TRUE`
3. Check that FCM token is not null in `app_user.fcm_token`
4. Look for Firebase errors in backend logs

### Invalid FCM tokens
- Tokens expire when users uninstall/reinstall the app
- The system logs warnings for invalid tokens
- Future enhancement: Remove invalid tokens from database automatically 