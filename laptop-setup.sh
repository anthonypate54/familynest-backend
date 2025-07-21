#!/bin/bash

# FamilyNest Laptop Setup Script
# Complete setup for portable development with Android phone connection

set -e  # Exit on any error

echo "🏠 FamilyNest Laptop Setup"
echo "=========================="
echo "This script will set up your laptop for portable FamilyNest development"
echo "with wireless Android phone connection."
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to wait for user confirmation
wait_for_user() {
    read -p "Press Enter to continue..."
}

echo "📋 Prerequisites Check"
echo "====================="

# Check Docker
if command_exists docker; then
    echo "✅ Docker is installed"
else
    echo "❌ Docker is not installed"
    echo "📖 Please install Docker Desktop from: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Check Docker Compose
if command_exists docker-compose; then
    echo "✅ Docker Compose is installed"
else
    echo "❌ Docker Compose is not installed"
    echo "📖 Please install Docker Compose"
    exit 1
fi

# Check Maven
if command_exists mvn; then
    echo "✅ Maven is installed"
else
    echo "❌ Maven is not installed"
    echo "📖 Please install Maven: brew install maven"
    exit 1
fi

# Check Java
if command_exists java; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    echo "✅ Java is installed (version: $JAVA_VERSION)"
else
    echo "❌ Java is not installed"
    echo "📖 Please install Java 17 or later"
    exit 1
fi

echo ""
echo "🚀 Starting Setup Process"
echo "========================="

# Step 1: Start Docker if needed
echo "📦 Step 1: Docker Setup"
if ! docker info >/dev/null 2>&1; then
    echo "🔄 Starting Docker..."
    open -a Docker
    echo "⏳ Waiting for Docker to start..."
    while ! docker info >/dev/null 2>&1; do
        sleep 2
    done
fi
echo "✅ Docker is running"

# Step 2: Build and start containers
echo ""
echo "🏗️ Step 2: Database Setup"
echo "Stopping any existing containers..."
docker-compose down -v >/dev/null 2>&1 || true

echo "🔄 Building and starting fresh containers..."
docker-compose up -d --build

echo "⏳ Waiting for database to be ready..."
sleep 15

# Verify containers are running
if docker-compose ps | grep -q "familynest-postgres.*Up"; then
    echo "✅ PostgreSQL container is running on port 5433"
else
    echo "❌ PostgreSQL container failed to start"
    docker-compose logs postgres
    exit 1
fi

if docker-compose ps | grep -q "familynest-backend.*Up"; then
    echo "✅ Backend container is running on port 8081"
else
    echo "❌ Backend container failed to start"
    docker-compose logs backend
    exit 1
fi

# Step 3: Test the setup
echo ""
echo "🧪 Step 3: Testing Setup"
echo "Testing Docker backend..."
DOCKER_TEST=$(curl -s -X POST http://localhost:8081/public/print \
    -H "Content-Type: application/json" \
    -d '{"message": "Docker setup test"}' || echo "failed")

if [[ "$DOCKER_TEST" == *"logged"* ]]; then
    echo "✅ Docker backend is working"
else
    echo "❌ Docker backend test failed"
    exit 1
fi

# Step 4: Network information
echo ""
echo "🌐 Step 4: Network Configuration"
echo "==============================="

# Get network IP
WIFI_IP=$(ifconfig en0 2>/dev/null | grep 'inet ' | awk '{print $2}')
ETHERNET_IP=$(ifconfig en1 2>/dev/null | grep 'inet ' | awk '{print $2}')

if [ -n "$WIFI_IP" ]; then
    LAPTOP_IP="$WIFI_IP"
    CONNECTION_TYPE="Wi-Fi"
elif [ -n "$ETHERNET_IP" ]; then
    LAPTOP_IP="$ETHERNET_IP"
    CONNECTION_TYPE="Ethernet"
else
    echo "❌ Could not detect network IP"
    exit 1
fi

echo "📡 Network Details:"
echo "   • Type: $CONNECTION_TYPE"
echo "   • IP Address: $LAPTOP_IP"
echo "   • Docker Backend: http://localhost:8081"
echo "   • Local Backend: Will run on http://localhost:8080"
echo "   • Android URL: http://$LAPTOP_IP:8080"

# Step 5: Setup complete
echo ""
echo "🎉 Setup Complete!"
echo "=================="
echo ""
echo "📱 For Android Connection:"
echo "   1. Make sure your Android phone is on the same network"
echo "   2. Update your Flutter app config to use: http://$LAPTOP_IP:8080"
echo "   3. Start the local backend with: ./start-laptop.sh"
echo ""
echo "🔧 Available Commands:"
echo "   • ./start-laptop.sh     - Start backend in laptop mode"
echo "   • ./get-laptop-ip.sh    - Get current IP address"
echo "   • docker-compose logs   - View container logs"
echo "   • docker-compose down   - Stop containers"
echo ""
echo "📚 Next Steps:"
echo "   1. Test with: ./start-laptop.sh"
echo "   2. Update your Android app configuration"
echo "   3. Test the connection from your phone"
echo ""

# Create a config file for the Android app
echo "📝 Creating Android config file..."
cat > android-config.txt << EOF
# FamilyNest Android Configuration
# Copy these settings to your Flutter app

Backend URL: http://$LAPTOP_IP:8080
Database: PostgreSQL (Docker container)
Port: 8080

# For development, update this in your Flutter app:
# lib/config/app_config.dart
# 
# Change the baseUrl to: http://$LAPTOP_IP:8080
EOF

echo "✅ Android configuration saved to: android-config.txt"
echo ""
echo "🚀 Ready to go! Run './start-laptop.sh' to start the backend." 