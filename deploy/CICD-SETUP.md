# CI/CD Setup — Git Push → Auto Deploy on Ubuntu

## Pipeline overview

```
Developer pushes to GitHub (main branch)
        │
        ▼
┌───────────────────┐
│  GitHub Actions   │
│  1. mvn test      │
│  2. mvn package   │
│  3. SCP JAR → VM  │
│  4. deploy.sh     │
└─────────┬─────────┘
          ▼
   Ubuntu VM restarts app
   (data/ DB preserved)
```

## One-time setup

### Step 1 — Push project to GitHub

```bash
git init
git add .
git commit -m "Initial commit: URL shortener"
git remote add origin https://github.com/YOUR_USER/url-shortener.git
git push -u origin main
```

### Step 2 — Create SSH key for deployment (on your Windows PC)

```powershell
ssh-keygen -t ed25519 -C "github-actions-deploy" -f deploy_key -N ""
```

- Copy **public key** to VM:

```powershell
type deploy_key.pub | ssh chandan@YOUR_VM_IP "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

- Copy **private key** content for GitHub secret (entire file including `BEGIN`/`END` lines)

### Step 3 — Allow deploy user to restart service without password

On Ubuntu VM:

```bash
sudo visudo -f /etc/sudoers.d/url-shortener-deploy
```

Add (replace `chandan` with your VM username):

```
chandan ALL=(ALL) NOPASSWD: /opt/url-shortener/deploy/deploy.sh
```

Copy deploy script to VM:

```bash
sudo mkdir -p /opt/url-shortener/deploy
sudo cp deploy/deploy.sh /opt/url-shortener/deploy/
sudo chmod +x /opt/url-shortener/deploy/deploy.sh
```

### Step 4 — Add GitHub Secrets

Go to: **GitHub repo → Settings → Secrets and variables → Actions → New repository secret**

| Secret | Value |
|--------|-------|
| `VM_HOST` | Your VM IP, e.g. `192.168.1.50` |
| `VM_USER` | SSH username, e.g. `chandan` |
| `VM_SSH_KEY` | Full private key from `deploy_key` |

### Step 5 — Test the pipeline

```bash
# Make any small change, commit, push
git add .
git commit -m "Test CI/CD deploy"
git push
```

Watch: **GitHub → Actions tab** — build should complete and VM app should restart.

Verify on VM:

```bash
sudo systemctl status url-shortener
curl http://localhost:8080/
```

## Workflows included

| File | Trigger | What it does |
|------|---------|--------------|
| `.github/workflows/ci.yml` | PR + push | Build, run tests, upload JAR artifact |
| `.github/workflows/deploy.yml` | push to main | Build JAR, SCP to VM, run deploy.sh |

## Manual deploy (without Git)

```bash
mvn clean package -DskipTests
scp target/url-shortener-1.0.0.jar chandan@VM_IP:/tmp/
ssh chandan@VM_IP "sudo /opt/url-shortener/deploy/deploy.sh /tmp/url-shortener-1.0.0.jar"
```

## Industry-level upgrades (later)

| Upgrade | Purpose |
|---------|---------|
| **Staging environment** | Deploy to test VM before production |
| **Docker + registry** | Push image to GHCR, pull on VM |
| **Health check endpoint** | `/actuator/health` before switching traffic |
| **Blue-green deploy** | Two instances, zero downtime |
| **Slack/Discord notify** | Alert on deploy success/failure |
| **SonarQube / CodeQL** | Security and code quality scans |
