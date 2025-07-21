#!/bin/bash

# FamilyNest Backend - Laptop Mode Startup Script
# This script starts the backend for wireless Android connection

echo "🚀 Starting FamilyNest Backend in Laptop Mode..."
echo "================================"

# Check if Docker containers are running
echo "📦 Checking Docker containers..."
if ! docker-compose ps | grep -q "familynest-postgres.*Up"; then
    echo "🔄 Starting Docker containers..."
    docker-compose up -d
    echo "⏳ Waiting for database to be ready..."
    sleep 10
else
    echo "✅ Docker containers are already running"
fi

# Get network information
echo ""
echo "🌐 Network Information:"
echo "================================"

# Get Wi-Fi IP address (works on macOS)
WIFI_IP=$(ifconfig en0 | grep 'inet ' | awk '{print $2}')
if [ -n "$WIFI_IP" ]; then
    echo "📱 Wi-Fi IP Address: $WIFI_IP"
    echo "🔗 Android should connect to: http://$WIFI_IP:8080"
else
    echo "❌ Could not detect Wi-Fi IP address"
    echo "🔍 Available network interfaces:"
    ifconfig | grep 'inet ' | grep -v '127.0.0.1'
fi

echo ""
echo "🎯 Backend will start on:"
echo "   • Local: http://localhost:8080"
echo "   • Network: http://$WIFI_IP:8080 (for Android)"
echo "   • Database: localhost:5433"
echo ""

# Start the backend with laptop profile
echo "🚀 Starting backend..."
echo "================================"
mvn spring-boot:run -Dspring-boot.run.profiles=laptop 