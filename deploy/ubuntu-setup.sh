#!/bin/bash
# Best URL Shortener — Ubuntu VM setup script
# Run on a fresh Ubuntu VM as a user with sudo access.
#
# Usage:
#   chmod +x ubuntu-setup.sh
#   ./ubuntu-setup.sh
#
# Before running: copy url-shortener-1.0.0.jar into /opt/url-shortener/
# Or build on VM: mvn clean package -DskipTests

set -euo pipefail

APP_DIR="/opt/url-shortener"
JAR_NAME="url-shortener-1.0.0.jar"
SERVICE_NAME="url-shortener"
DOMAIN="besturlshortener.com"

echo "==> Updating system packages..."
sudo apt update
sudo apt upgrade -y

echo "==> Installing Java 17, Nginx, Maven (for optional build)..."
sudo apt install -y openjdk-17-jdk nginx maven curl

java -version

echo "==> Creating app user and directories..."
sudo useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin urlshortener 2>/dev/null || true
sudo mkdir -p "$APP_DIR/data"
sudo chown -R urlshortener:urlshortener "$APP_DIR"

if [ ! -f "$APP_DIR/$JAR_NAME" ]; then
  echo "ERROR: $APP_DIR/$JAR_NAME not found."
  echo "Copy the JAR first, e.g.:"
  echo "  scp target/$JAR_NAME user@VM_IP:$APP_DIR/"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Installing systemd service (H2 database, no PostgreSQL)..."
sudo cp "$SCRIPT_DIR/url-shortener-h2.service" "/etc/systemd/system/${SERVICE_NAME}.service"
sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE_NAME"
sudo systemctl restart "$SERVICE_NAME"

echo "==> Configuring Nginx..."
sudo cp "$SCRIPT_DIR/../nginx/besturlshortener.conf" /etc/nginx/sites-available/besturlshortener.conf
sudo ln -sf /etc/nginx/sites-available/besturlshortener.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl reload nginx

echo "==> Opening firewall port 80 (if ufw is active)..."
if sudo ufw status | grep -q "Status: active"; then
  sudo ufw allow 80/tcp
  sudo ufw allow OpenSSH
fi

VM_IP=$(hostname -I | awk '{print $1}')
echo ""
echo "=============================================="
echo " Deployment complete!"
echo "=============================================="
echo " VM IP:        $VM_IP"
echo " App status:   sudo systemctl status $SERVICE_NAME"
echo " Nginx status: sudo systemctl status nginx"
echo ""
echo " Test on VM:"
echo "  curl http://localhost:8080/"
echo "  curl http://localhost/"
echo ""
echo " On your PC, add to hosts file:"
echo "  $VM_IP    $DOMAIN"
echo "  $VM_IP    www.$DOMAIN"
echo ""
echo " Then open: http://$DOMAIN"
echo "=============================================="
