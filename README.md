# Best URL Shortener

A Spring Boot URL shortener designed for deployment on a VM, exposed to your local network via Nginx with a friendly domain like `http://besturlshortener.com`.

## Architecture

```
Browser (your LAN)
       │
       ▼
  Nginx :80  (besturlshortener.com)
       │
       ▼
  Spring Boot :8080
       │
       ├── Caffeine cache (hot redirects)
       └── H2 / PostgreSQL (persistent mappings)
```

## How Shortening Works (DSA)

| Step | Algorithm | Complexity | Why |
|------|-----------|------------|-----|
| Generate code | Auto-increment ID → **Base62** | O(log₆₂ n) encode | Bijective, no hash collisions |
| Lookup redirect | Indexed `short_code` column | O(1) DB lookup | Fast redirect path |
| Deduplicate URLs | **SHA-256** hash index | O(1) average | Same long URL → same short link |
| Hot path cache | **Caffeine** LRU cache | O(1) | Avoid DB on repeated clicks |

**Base62** maps numeric IDs to `a-zA-Z0-9`:

- ID `1` → `b`
- ID `62` → `ba`
- ID `1,000,000` → `4c92` (4 characters)

This is the same core idea used by production shorteners (counter + encoding), not random hashing (which causes collisions).

## Project Structure

```
src/main/java/com/besturlshortener/
├── UrlShortenerApplication.java
├── config/          # Cache, app properties
├── controller/      # REST API + redirect + home page
├── dto/             # Request/response records
├── entity/          # JPA UrlMapping
├── exception/       # Global error handling
├── repository/      # Spring Data JPA
├── service/         # Shortening business logic
└── util/Base62.java # Encoder/decoder

nginx/               # Nginx config for VM
deploy/              # systemd service file
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Web UI |
| `POST` | `/api/shorten` | Create short URL |
| `GET` | `/api/stats/{code}` | Click stats |
| `GET` | `/{code}` | Redirect to original URL |

### Example

```bash
curl -X POST http://besturlshortener.com/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com/search?q=spring+boot"}'
```

Response:

```json
{
  "shortCode": "b",
  "shortUrl": "http://besturlshortener.com/b",
  "originalUrl": "https://www.google.com/search?q=spring+boot",
  "createdAt": "2026-06-05T10:00:00Z"
}
```

---

## Step-by-Step: Build & Expose on Your Network

### Step 1 — Prerequisites (VM)

On your Linux VM:

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk nginx
java -version
```

### Step 2 — Build the Application

**Requires JDK 17+.** On your dev machine (or VM):

```bash
cd url-shortener
mvn clean package -DskipTests
```

**No JDK 17 locally?** Use Docker instead:

```bash
docker compose up -d --build
```

This starts Spring Boot + Nginx on ports 8080 and 80.

JAR output: `target/url-shortener-1.0.0.jar`

### Step 3 — Copy to VM

```bash
scp target/url-shortener-1.0.0.jar user@YOUR_VM_IP:/opt/url-shortener/
scp -r nginx deploy user@YOUR_VM_IP:/opt/url-shortener/
```

### Step 4 — Run Spring Boot on the VM

**Quick start (H2 file DB, good for testing):**

```bash
cd /opt/url-shortener
java -jar url-shortener-1.0.0.jar \
  --app.base-url=http://besturlshortener.com
```

**Production (PostgreSQL + systemd):**

```bash
# Install PostgreSQL
sudo apt install -y postgresql
sudo -u postgres psql -c "CREATE USER urlshortener WITH PASSWORD 'changeme';"
sudo -u postgres psql -c "CREATE DATABASE urlshortener OWNER urlshortener;"

# Install systemd service
sudo cp /opt/url-shortener/deploy/url-shortener.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable url-shortener
sudo systemctl start url-shortener
sudo systemctl status url-shortener
```

Verify locally on VM:

```bash
curl http://localhost:8080/
```

### Step 5 — Configure Nginx

```bash
sudo cp /opt/url-shortener/nginx/besturlshortener.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/besturlshortener.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Open firewall (if enabled):

```bash
sudo ufw allow 80/tcp
```

### Step 6 — Make `besturlshortener.com` Resolve on Your LAN

Pick **one** option:

#### Option A — Hosts file (easiest, per device)

On every PC/phone that should use the shortener, add:

```
YOUR_VM_IP    besturlshortener.com
YOUR_VM_IP    www.besturlshortener.com
```

- **Windows:** `C:\Windows\System32\drivers\etc\hosts` (edit as Administrator)
- **Linux/Mac:** `/etc/hosts`
- **Android/iOS:** usually requires a local DNS app or router DNS

#### Option B — Router local DNS (best for whole network)

If your router supports local DNS records (pfSense, OpenWrt, UniFi, etc.):

| Hostname | IP |
|----------|-----|
| `besturlshortener.com` | `YOUR_VM_IP` |
| `www.besturlshortener.com` | `YOUR_VM_IP` |

All devices on DHCP will resolve the domain automatically.

#### Option C — dnsmasq on the VM (LAN DNS server)

```bash
sudo apt install -y dnsmasq
echo "address=/besturlshortener.com/YOUR_VM_IP" | sudo tee /etc/dnsmasq.d/shortener.conf
sudo systemctl restart dnsmasq
```

Point your router's DNS to the VM IP.

### Step 7 — Test End-to-End

From any device on your network:

1. Open `http://besturlshortener.com` → you should see the shortener UI
2. Paste a long URL and click **Shorten**
3. Open the short link → it should redirect

```bash
# From terminal
curl -I http://besturlshortener.com/b
# Expect: HTTP/1.1 302 → original URL
```

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | Spring Boot port (internal) |
| `app.base-url` | `http://besturlshortener.com` | Prefix for generated short URLs |
| `spring.profiles.active` | — | Set to `prod` for PostgreSQL |

Environment variables (prod profile):

- `APP_BASE_URL`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`

---

## Local Development (Windows)

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`

To simulate the domain locally, add to `C:\Windows\System32\drivers\etc\hosts`:

```
127.0.0.1    besturlshortener.com
```

Then run with:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.base-url=http://besturlshortener.com
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Domain doesn't resolve | Check hosts file or router DNS points to VM IP |
| Nginx 502 Bad Gateway | Ensure Spring Boot is running on port 8080 |
| Short URL redirects to wrong host | Set `app.base-url` to your public LAN domain |
| Can't reach VM from LAN | Check VM firewall / cloud security group allows port 80 |

---

## License

MIT — use freely on your home lab or internal network.
