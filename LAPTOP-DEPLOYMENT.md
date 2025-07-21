# FamilyNest Laptop Deployment Guide

This guide helps you set up FamilyNest backend on your laptop for portable development with wireless Android phone connection.

## 🎯 What This Setup Provides

- **Portable Backend**: Run FamilyNest backend on any laptop
- **Wireless Android Connection**: Connect your Android phone wirelessly to the laptop backend
- **Fresh Database**: Clean PostgreSQL database in Docker containers
- **Easy Deployment**: One-command setup and startup

## 📋 Prerequisites

Before starting, ensure you have:

- **Docker Desktop** installed and running
- **Java 17+** installed 
- **Maven** installed
- **macOS/Linux** (scripts are designed for Unix-like systems)

## 🚀 Quick Start

### 1. Complete Setup (First Time)
```bash
./laptop-setup.sh
```

This script will:
- ✅ Check all prerequisites
- 🐳 Start Docker containers (PostgreSQL + Backend)
- 🧪 Test the setup
- 🌐 Display network configuration for Android
- 📝 Create Android configuration file

### 2. Daily Usage (After Setup)
```bash
./start-laptop.sh
```

This starts the backend in laptop mode and shows your IP address for Android connection.

### 3. Get IP Address Only
```bash
./get-laptop-ip.sh
```

Quick way to find your laptop's IP address for Android configuration.

## 🌐 Network Configuration

### Backend Ports
- **Docker Backend**: `http://localhost:8081` (for testing)
- **Local Backend**: `http://localhost:8080` (for Android connection)
- **Database**: `localhost:5433`

### Android Connection
Your Android phone should connect to: `http://YOUR_LAPTOP_IP:8080`

Example: `http://10.0.0.9:8080`

## 📱 Android App Configuration

Update your Flutter app's configuration:

**File**: `familynest/lib/config/app_config.dart`

```dart
// Change this line to your laptop's IP:
static const String baseUrl = 'http://10.0.0.9:8080';  // Replace with your IP
```

**Important**: 
- Your Android phone must be on the same Wi-Fi network as your laptop
- Use the IP address shown by the setup scripts
- Use port 8080 (not 8081)

## 🐳 Docker Details

### Services
- **PostgreSQL**: Fresh database with all migrations applied
- **Backend**: Dockerized version for testing (runs on 8081)

### Useful Commands
```bash
# View container status
docker-compose ps

# View logs
docker-compose logs backend
docker-compose logs postgres

# Stop containers
docker-compose down

# Stop and remove all data
docker-compose down -v

# Restart containers
docker-compose restart
```

## 🔧 Profiles and Configuration

### Laptop Profile
The backend uses the `laptop` Spring profile with these settings:
- Database: Docker PostgreSQL on localhost:5433
- Server: Binds to all interfaces (0.0.0.0:8080)
- CORS: Allows all origins for Android connection
- Flyway: Disabled (uses existing Docker database)

### Configuration Files
- `application-laptop.properties`: Laptop-specific settings
- `application-docker.properties`: Docker container settings
- `docker-compose.yml`: Container orchestration

## 🧪 Testing the Setup

### Test Docker Backend
```bash
curl -X POST http://localhost:8081/public/print \
  -H "Content-Type: application/json" \
  -d '{"message": "Docker test"}'
```

### Test Local Backend (when running)
```bash
curl -X POST http://localhost:8080/public/print \
  -H "Content-Type: application/json" \
  -d '{"message": "Local test"}'
```

### Test from Android
Use your Android app or test with curl from another device:
```bash
curl -X POST http://YOUR_LAPTOP_IP:8080/public/print \
  -H "Content-Type: application/json" \
  -d '{"message": "Android test"}'
```

## 🚨 Troubleshooting

### Android Can't Connect
1. **Check Network**: Ensure phone and laptop are on same Wi-Fi
2. **Check IP**: Run `./get-laptop-ip.sh` to verify current IP
3. **Check Firewall**: macOS may block incoming connections
4. **Check Backend**: Ensure backend is running with `laptop` profile

### Docker Issues
1. **Containers Not Starting**: Run `docker-compose logs` to see errors
2. **Port Conflicts**: Ensure ports 5433 and 8081 are available
3. **Permission Issues**: Ensure Docker has sufficient permissions

### Backend Issues
1. **Maven Issues**: Ensure Java 17+ and Maven are properly installed
2. **Database Connection**: Verify PostgreSQL container is healthy
3. **Profile Issues**: Ensure using `laptop` profile for local backend

### Network Issues
1. **IP Detection Failed**: Manually check with `ifconfig`
2. **Multiple Networks**: Choose the one your phone connects to
3. **VPN Interference**: Disable VPN if causing issues

## 📁 File Structure

```
familynest-backend/
├── laptop-setup.sh              # Complete setup script
├── start-laptop.sh              # Daily startup script  
├── get-laptop-ip.sh             # IP detection script
├── docker-compose.yml           # Docker container setup
├── Dockerfile                   # Backend container build
├── LAPTOP-DEPLOYMENT.md         # This documentation
├── android-config.txt           # Generated Android settings
└── src/main/resources/
    ├── application-laptop.properties    # Laptop profile
    └── application-docker.properties    # Docker profile
```

## 🎯 Use Cases

This setup is perfect for:
- ✈️ **Travel Development**: Work on FamilyNest while traveling
- 🏠 **Home Development**: Alternative to complex local setup
- 🧪 **Testing**: Fresh environment for testing changes
- 👥 **Team Development**: Easy setup for new team members
- 🔄 **Demos**: Portable demo environment

## 📈 Next Steps

After successful setup:
1. Test basic API endpoints from Android
2. Upload photos/videos to verify file handling
3. Test push notifications (may need Firebase setup)
4. Test real-time messaging via WebSocket

## 💡 Tips

- **Keep Scripts Updated**: Scripts detect current IP automatically
- **Restart Network Changes**: If you change networks, restart backend
- **Monitor Resources**: Docker containers use CPU/memory
- **Regular Updates**: Pull latest code before traveling
- **Backup Data**: Docker volumes persist data between restarts

---

**Need Help?** Check the troubleshooting section or run the scripts with verbose output. 