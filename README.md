# Patient AI Operations Portal

Microservices platform for explainable patient risk, intervention what-if, causal guardrails under stress, and a browser UI served by the API gateway.

**Stack:** Java 21, Spring Boot, PostgreSQL, Kafka, ZooKeeper, gRPC, optional Gemini chat. **Run everything with Docker Compose.**

---

## Demo Login and Password

I have hosted a version of this on DigitalOcean for demonstration. The link is http://159.65.104.236:4004.

Use the following credentials to access it:  
Email : testuser@testin.com  
Password : 12345678  

## Prerequisites

- **Docker Desktop** (Windows/macOS) or **Docker Engine + Compose** (Linux). Daemon must be running.
- **Git**
- Optional: **Gemini API key** for the AI Copilot (`GEMINI_API_KEY`). Chat still works with rule-based fallbacks if unset.

---

## Run locally (step by step)

### 1) Clone and enter the repo

```powershell
git clone <your-repo-url>
cd java-spring-microservices
```

### 2) Environment file

```powershell
copy .env.example .env
```

Edit `.env` and set **`GEMINI_API_KEY`** (and optionally **`GOOGLE_API_KEY`**) if you want cloud LLM replies. Save the file.  
**Never commit `.env`** (it is gitignored).

### 3) Start the stack

From the **repository root** (where `docker-compose.yml` lives):

```powershell
docker compose up -d --build
```

First startup can take several minutes (images + Maven builds).

### 4) Confirm containers

```powershell
docker compose ps
```

Wait until core services are **running** / **healthy** (Kafka may take a short while).

### 5) Open the app

**URL:** [http://localhost:4004](http://localhost:4004)

Default gateway base URL in the UI is `http://localhost:4004`; leave it as-is for local use.

**Zipkin (tracing):** [http://localhost:9411](http://localhost:9411)

### 6) Sign in

Use credentials from your auth seed data (for example **`testuser@test.com`** / **`password`** if that user exists in `auth-service` seed SQL), or **Create Account** on the portal.

---

## Stop / reset

**Stop without deleting data:**

```powershell
docker compose down
```

**Full reset** (PostgreSQL volume cleared; fixes many Kafka/ZooKeeper stale states):

```powershell
docker compose down -v
docker compose up -d --build
```

---

## Repo layout (short)

| Path | Role |
|------|------|
| `docker-compose.yml` | Full stack orchestration |
| `docker/init-databases.sql` | Postgres databases on first boot |
| `api-gateway/` | Gateway + static SPA (`src/main/resources/static/`) |
| `auth-service/`, `patient-service/`, `billing-service/`, `analytics-service/`, `ai-service/` | Microservices |
| `healthcare_dataset*.csv` | Optional bulk import in Patient Records |
| `scripts/` | Optional offline helpers (e.g. dataset augmentation, ML baseline) |
| `load-test/` | Optional PowerShell load scripts |
| `infrastructure/` | AWS CDK (optional; not required for local Compose) |

---

## Hosting on a VPS (HTTPS, public URL)

1. Push this repo to GitHub (including `docker-compose.yml` and `docker/`).  
2. On a Linux VPS: install Docker, clone the repo, copy `.env.example` → `.env`, run `docker compose up -d --build`.  
3. Put a reverse proxy (e.g. **Caddy** or **nginx**) in front with TLS; proxy to **`127.0.0.1:4004`**.  
4. In the portal **Settings**, set **Gateway Base URL** to your public **`https://your-domain`** (not `localhost`).  
5. Firewall: allow **22**, **80**, **443** only; do not expose Postgres/Kafka ports publicly.

---

## Demo workflow (quick)

1. **Patient Records** — import or create patients; click a row to set **active patient** for chat/AI.  
2. **Digital Twin** — assess risk, anomalies, intervention.  
3. **Chaos Center** — stress presets and guardrail decision.  
4. **Experiments / Findings** — scenario suite and exports as needed.  
5. **AI Copilot** (floating control) — ask risk, definitions, or portal questions.

---

## Troubleshooting

| Issue | What to try |
|--------|--------------|
| Gateway connection refused | `docker compose ps`; ensure **api-gateway** is up; wait after rebuild. |
| Kafka / ZooKeeper errors after restart | `docker compose down -v` then `up -d --build`. |
| Chat always fallback / errors | Set **`GEMINI_API_KEY`** in `.env`; restart **ai-service**: `docker compose up -d ai-service`. |
| Port already in use | Stop other apps using **4004**, **5432**, **9092**, etc., or change ports in `docker-compose.yml`. |

