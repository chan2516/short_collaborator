# Ubuntu VM Deployment Guide

## JAR or Tomcat?

This app is deployed as a **standalone JAR** — not as a WAR on external Tomcat.

| Format | Used here? | Explanation |
|--------|------------|-------------|
| **JAR (fat JAR)** | ✅ Yes | Spring Boot bundles Tomcat inside the JAR. Run with `java -jar app.jar` |
| **WAR + Tomcat** | ❌ No | Old style — deploy `.war` into a separate Tomcat server |
| **Docker** | Optional | Alternative if you prefer containers |

**Your stack:**

```
Internet/LAN  →  Nginx (:80)  →  Spring Boot JAR (:8080)  →  H2/PostgreSQL
```

Nginx handles the public domain. Spring Boot runs as a background service via **systemd**.

---

## What You Need

- Fresh **Ubuntu 22.04 or 24.04** VM
- SSH access to the VM
- Project files on your Windows PC (`c:\WORK\WORKING\test`)
- VM LAN IP (example: `192.168.1.50`)

---

## Part 1 — Build the JAR (on Windows or Ubuntu)

### Option A: Build on Ubuntu VM (recommended)

```bash
# SSH into VM
ssh youruser@192.168.1.50

# Install build tools
sudo apt update
sudo apt install -y openjdk-17-jdk maven git

# Copy project to VM (from Windows PowerShell)
# scp -r c:\WORK\WORKING\test youruser@192.168.1.50:~/url-shortener

cd ~/url-shortener
mvn clean package -DskipTests
```

JAR created at: `target/url-shortener-1.0.0.jar`

### Option B: Build on Windows (needs JDK 17+)

```powershell
cd c:\WORK\WORKING\test
mvn clean package -DskipTests
scp target\url-shortener-1.0.0.jar youruser@192.168.1.50:~/
```

---

## Part 2 — Install on Ubuntu VM (step by step)

### Step 1: Connect to VM

```bash
ssh youruser@192.168.1.50
```

### Step 2: Update Ubuntu & install Java + Nginx

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk nginx curl

java -version
# Should show: openjdk version "17.x.x"
```

### Step 3: Create application folder

```bash
sudo mkdir -p /opt/url-shortener/data
sudo useradd --system --home /opt/url-shortener --shell /usr/sbin/nologin urlshortener 2>/dev/null || true
```

### Step 4: Copy JAR and config files

```bash
# If JAR is in home directory
sudo cp ~/url-shortener-1.0.0.jar /opt/url-shortener/

# If you copied full project
sudo cp ~/url-shortener/target/url-shortener-1.0.0.jar /opt/url-shortener/
sudo cp ~/url-shortener/deploy/url-shortener-h2.service /opt/url-shortener/deploy/
sudo cp ~/url-shortener/nginx/besturlshortener.conf /opt/url-shortener/nginx/

sudo chown -R urlshortener:urlshortener /opt/url-shortener
```

### Step 5: Create systemd service (auto-start on boot)

```bash
sudo cp /opt/url-shortener/deploy/url-shortener-h2.service /etc/systemd/system/url-shortener.service

sudo systemctl daemon-reload
sudo systemctl enable url-shortener
sudo systemctl start url-shortener
sudo systemctl status url-shortener
```

You should see **active (running)**.

Test internally:

```bash
curl http://localhost:8080/
```

### Step 6: Configure Nginx (public port 80)

```bash
sudo cp /opt/url-shortener/nginx/besturlshortener.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/besturlshortener.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

sudo nginx -t
sudo systemctl reload nginx
```

Test via Nginx:

```bash
curl http://localhost/
```

### Step 7: Open firewall (if enabled)

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw enable   # only if not already enabled
sudo ufw status
```

### Step 8: Point domain to VM on your network

Get VM IP:

```bash
hostname -I
# Example: 192.168.1.50
```

On your **Windows PC**, edit `C:\Windows\System32\drivers\etc\hosts` as Administrator:

```
192.168.1.50    besturlshortener.com
192.168.1.50    www.besturlshortener.com
```

### Step 9: Test from your PC

Open browser: **http://besturlshortener.com**

Or:

```powershell
curl http://besturlshortener.com/
```

---

## Quick automated setup

If JAR and project are already on the VM:

```bash
cd ~/url-shortener/deploy
chmod +x ubuntu-setup.sh
sudo cp ../target/url-shortener-1.0.0.jar /opt/url-shortener/
./ubuntu-setup.sh
```

---

## Useful commands

| Task | Command |
|------|---------|
| View app logs | `sudo journalctl -u url-shortener -f` |
| Restart app | `sudo systemctl restart url-shortener` |
| Stop app | `sudo systemctl stop url-shortener` |
| Nginx logs | `sudo tail -f /var/log/nginx/besturlshortener.error.log` |
| Check port 8080 | `sudo ss -tlnp \| grep 8080` |

---

## Upgrade to PostgreSQL (optional, later)

For heavier use, switch from H2 file DB to PostgreSQL:

```bash
sudo apt install -y postgresql
sudo -u postgres psql -c "CREATE USER urlshortener WITH PASSWORD 'YourStrongPassword';"
sudo -u postgres psql -c "CREATE DATABASE urlshortener OWNER urlshortener;"

sudo cp /opt/url-shortener/deploy/url-shortener.service /etc/systemd/system/
# Edit password in service file, then:
sudo systemctl daemon-reload
sudo systemctl restart url-shortener
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `502 Bad Gateway` | App not running — `sudo systemctl status url-shortener` |
| `Connection refused` on :80 | Nginx not running — `sudo systemctl start nginx` |
| Domain not opening | Check hosts file has correct VM IP |
| `java: command not found` | Install JDK 17 — `sudo apt install openjdk-17-jdk` |
| Permission denied on data | `sudo chown -R urlshortener:urlshortener /opt/url-shortener` |
