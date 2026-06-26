#!/usr/bin/env bash
# Connect to Android device via wireless ADB (Tailnet/LAN)
set -euo pipefail

DEFAULT_HOST="s23"

echo "=== Doozle: Wireless ADB Connect ==="
echo ""

# Device host
read -rp "Device hostname/IP [$DEFAULT_HOST]: " host
host="${host:-$DEFAULT_HOST}"

# Check if already connected
if adb devices 2>/dev/null | grep -q "$host"; then
    echo "Already connected to $host"
    adb devices
    exit 0
fi

# Ask if pairing is needed
echo ""
echo "Do you need to pair? (only needed first time per device)"
echo "  On phone: Settings > Developer Options > Wireless debugging > Pair device"
read -rp "Pair now? [y/N]: " do_pair

if [[ "${do_pair,,}" == "y" ]]; then
    read -rp "Pairing port (shown on phone): " pair_port
    read -rp "Pairing code (6 digits): " pair_code

    echo ""
    echo "Pairing with $host:$pair_port ..."
    adb pair "$host:$pair_port" "$pair_code"
    echo ""
fi

# Connect
echo "Enter the connect port (shown on Wireless debugging main screen, NOT the pairing port)"
read -rp "Connect port: " connect_port

echo "Connecting to $host:$connect_port ..."
adb connect "$host:$connect_port"

echo ""
echo "Connected devices:"
adb devices
