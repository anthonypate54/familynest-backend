#!/bin/bash

# Quick script to get laptop IP for Android connection

echo "🔍 Finding your laptop's IP address for Android connection..."
echo ""

# Try different network interfaces common on macOS
WIFI_IP=$(ifconfig en0 2>/dev/null | grep 'inet ' | awk '{print $2}')
ETHERNET_IP=$(ifconfig en1 2>/dev/null | grep 'inet ' | awk '{print $2}')

if [ -n "$WIFI_IP" ]; then
    echo "📡 Wi-Fi (en0): $WIFI_IP"
    echo "🔗 Android URL: http://$WIFI_IP:8080"
fi

if [ -n "$ETHERNET_IP" ]; then
    echo "🔌 Ethernet (en1): $ETHERNET_IP"  
    echo "🔗 Android URL: http://$ETHERNET_IP:8080"
fi

if [ -z "$WIFI_IP" ] && [ -z "$ETHERNET_IP" ]; then
    echo "❌ Could not auto-detect IP. Here are all network interfaces:"
    echo ""
    ifconfig | grep -A 1 'flags=.*UP' | grep 'inet ' | grep -v '127.0.0.1'
fi

echo ""
echo "💡 Tips:"
echo "   • Make sure your Android phone is on the same Wi-Fi network"
echo "   • Use the Wi-Fi IP address in your Android app configuration"
echo "   • The backend should be running on port 8080" 