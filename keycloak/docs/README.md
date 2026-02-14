# Mideye Magic Link for Keycloak — Technical Documentation

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Security Model](#security-model)
- [Dashboard Authentication & Authorization](#dashboard-authentication--authorization)
- [Environment Variables Reference](#environment-variables-reference)
- [Authenticator Configuration Reference](#authenticator-configuration-reference)
- [REST API Reference](#rest-api-reference)
- [Deployment Guide](#deployment-guide)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

The Mideye Magic Link Keycloak integration consists of two SPI components deployed as a single JAR:

```
┌─────────────────────────────────────────────────────────────┐
│                     Keycloak 26.x                           │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │        MideyeMagicLinkAuthenticator (per-realm)      │   │
│  │  (reads config from Admin Console, calls Mideye API,  │   │
│  │   records events to per-realm cache)                 │   │
│  └──────────────────────┬───────────────────────────────┘   │
│                         │                                   │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          MagicLinkEventCache (per-realm)             │   │
│  │  (in-memory ring buffer — one per realm)             │   │
│  └──────────────────────────────────────────────────────┘   │
│                         │                                   │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          MagicLinkDashboardResource (per-realm)      │   │
│  │  (REST API + dashboard — per-realm + master admin)   │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│  Mideye Server  │
│  Magic Link API │
│  (HTTPS)        │
└─────────────────┘
```

### Data flow

1. **Authentication** — `MideyeMagicLinkAuthenticator` loads per-realm config from the Admin Console (`AuthenticatorConfigModel`), reads the user's `phoneNumber` attribute, and calls the Mideye Server Magic Link API (`GET /api/sfwa/auth?msisdn={phone}`). The call blocks until the user responds (push/SMS) or the timeout expires. The outcome is recorded in the per-realm `MagicLinkEventCache`.
2. **Dashboard** — `MagicLinkDashboardResource` provides a web dashboard and REST API for viewing authentication events, statistics, configuration, and top users. All endpoints require authentication + the `mideye-magic-link-admin` realm role (or the `mideye-magic-link-master` role in the master realm for cross-realm access).
3. **Cache** — `MagicLinkEventCache` is an in-memory per-realm cache. Each realm has its own events, statistics, and top-user data. Lost on Keycloak restart.

---

## Security Model

### Authentication failures don't stall

If Mideye Server is unreachable or returns an error, the authentication step **fails** (the user cannot proceed past MFA). This is the correct behavior for a second-factor — unlike Shield's fail-open design, an MFA step should not be silently skipped.

However, if the authenticator is **not configured** (no URL or API key), it logs a clear error and fails the authentication step. This prevents misconfigured deployments from silently allowing passwordless access.

### Dashboard security

All dashboard and API endpoints are protected at the SPI level:

| Layer | Protection |
|-------|-----------|
| **Authentication** | Cookie-based session (browser) or Bearer token (API). Unauthenticated browser requests redirect to the Keycloak OIDC login page. |
| **Authorization (per-realm)** | The authenticated user must have the `mideye-magic-link-admin` realm role (configurable per realm via `mideye.dashboardRole` in the Admin Console). |
| **Authorization (master)** | Alternatively, a user in the **master** realm with the `mideye-magic-link-master` role can access any realm's dashboard. |

### Phone number privacy

Phone numbers are **masked** in the event log (e.g., `***4567`). Only the last 4 digits are visible in the dashboard. Full phone numbers are never stored in the event cache.

### API key handling

The API key is stored as a `PASSWORD` type in the Keycloak Admin Console, so it's masked in the UI. When the configuration API (`/api/config`) returns settings, the API key is fully redacted.

---

## Dashboard Authentication & Authorization

### How it works

The `MagicLinkDashboardResource` class protects all endpoints with two methods:

- **`checkDashboardAuth()`** — used by the HTML dashboard endpoint (`/dashboard`). If the user has no session, it redirects to the Keycloak OIDC login page. After login, the user is redirected back to the dashboard. If the user is authenticated but lacks the required role, a styled 403 page is shown.

- **`checkAuth()`** — used by all JSON API endpoints (`/api/*`). Tries cookie-based auth first, falls back to Bearer token auth. Returns a JSON 401 or 403 response if authentication or authorization fails.

### Authorization model

Access is granted if **either** condition is met:

1. **Master admin** — the user exists in the **master** realm and has the `mideye-magic-link-master` role → can access **any** realm's dashboard
2. **Realm admin** — the user has the `mideye-magic-link-admin` role (or a custom role configured via `mideye.dashboardRole`) in the **current** realm

### Authentication methods

| Method | Used by | How |
|--------|---------|-----|
| **Session cookie** | Browser (dashboard HTML + AJAX) | Keycloak sets `KEYCLOAK_SESSION` cookie after OIDC login |
| **Bearer token** | External API callers (curl, scripts) | `Authorization: Bearer <access_token>` header |

### Setting up access

#### Option A: Per-realm access with `mideye-magic-link-admin`

##### Step 1: Create the realm role

1. Log in to the **Keycloak Admin Console** (e.g., http://localhost:8080/admin)
2. Select the target realm (e.g., `mideye-test`)
3. Go to **Realm roles** in the left sidebar
4. Click **Create role**
5. Enter `mideye-magic-link-admin` as the role name
6. Click **Save**

##### Step 2: Assign the role to users

1. Go to **Users** in the left sidebar
2. Click on the user who needs dashboard access
3. Go to the **Role mapping** tab
4. Click **Assign role**
5. Find and select `mideye-magic-link-admin`
6. Click **Assign**

#### Option B: Master realm cross-realm access with `mideye-magic-link-master`

##### Step 1: Create the role in the master realm

1. Log in to the Keycloak Admin Console
2. Select the **master** realm
3. Go to **Realm roles** → **Create role**
4. Enter `mideye-magic-link-master` as the role name
5. Click **Save**

##### Step 2: Assign the role

1. In the **master** realm, go to **Users**
2. Click on the user who needs cross-realm dashboard access
3. Go to the **Role mapping** tab → **Assign role** → select `mideye-magic-link-master`

Users with this role can now access the Magic Link dashboard in **any** realm.

#### Step 3: Access the dashboard

Each realm has its **own** Magic Link dashboard at a predictable URL:

```
http://<keycloak-host>/realms/<realm>/mideye-magic-link/dashboard
```

| Realm | Dashboard URL |
|-------|---------------|
| `company-a` | `https://keycloak.example.com/realms/company-a/mideye-magic-link/dashboard` |
| `company-b` | `https://keycloak.example.com/realms/company-b/mideye-magic-link/dashboard` |
| `mideye-test` | `http://localhost:8080/realms/mideye-test/mideye-magic-link/dashboard` |

Each dashboard shows only that realm's authentication events, statistics, and configuration. Data is completely isolated between realms.

### Custom role name per realm

To use a different role name in a specific realm, configure `mideye.dashboardRole` in the Admin Console:

1. Go to **Authentication** → your Magic Link flow
2. Click the **⚙ gear icon** next to "Mideye Magic Link"
3. Set **Dashboard role** to your custom role name
4. Click **Save**

### API access with Bearer tokens

For programmatic access (scripts, monitoring, CI/CD), obtain a token and pass it as a Bearer token:

```bash
# 1. Get an access token
TOKEN=$(curl -s -X POST \
  "http://localhost:8080/realms/mideye-test/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=security-admin-console&username=admin&password=admin" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# 2. Call any Magic Link API endpoint
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/stats
```

> **Note:** The user whose credentials you use must have the `mideye-magic-link-admin` realm role (or `mideye-magic-link-master` in the master realm).

---

## Environment Variables Reference

Environment variables are set on the Keycloak container (e.g., in `docker-compose.yml`). They serve as **fallback** when the corresponding Admin Console fields are left empty.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MIDEYE_URL` | No* | — | Mideye Server base URL (e.g., `https://mideye.example.com:8443`). *Required if not set in Admin Console. |
| `MIDEYE_API_KEY` | No* | — | Magic Link API key. *Required if not set in Admin Console. |

> **Note:** All other settings (phone attribute, timeout, TLS, dashboard role, event log) are configured exclusively via the Admin Console. Only the API credentials support environment variable fallback for backward compatibility.

---

## Authenticator Configuration Reference

These settings are configured in the Keycloak Admin Console under **Authentication → Flow → Mideye Magic Link → ⚙ Settings**.

### Connection settings

| Setting | Config key | Default | Description |
|---------|-----------|---------|-------------|
| Mideye Server URL | `mideye.url` | *(from env var)* | Base URL of the Mideye Server (e.g., `https://mideye.example.com:8443`). Falls back to `MIDEYE_URL` env var. |
| API Key | `mideye.apiKey` | *(from env var)* | API key for the Magic Link endpoint. Falls back to `MIDEYE_API_KEY` env var. Displayed as a password field in the Admin Console. |

### Behavior settings

| Setting | Config key | Default | Description |
|---------|-----------|---------|-------------|
| Phone number attribute | `mideye.phoneAttribute` | `phoneNumber` | Keycloak user attribute storing the phone number in E.164 format (e.g., `+46701234567`). |
| API timeout (seconds) | `mideye.timeoutSeconds` | `120` | How long to wait for the Mideye API response. The Magic Link call blocks until the user responds, so this should be 60–180 seconds. |
| Skip TLS verification | `mideye.skipTlsVerify` | `false` | Skip TLS certificate verification. **Never enable in production.** |

### Dashboard / Event log settings

| Setting | Config key | Default | Description |
|---------|-----------|---------|-------------|
| Event log max size | `mideye.eventLogMaxSize` | `1000` | Maximum events in the in-memory log per realm. Older events are discarded when the limit is reached. |
| Event TTL (hours) | `mideye.eventTtlHours` | `1` | Events older than this are pruned from the in-memory log. |
| Dashboard role | `mideye.dashboardRole` | `mideye-magic-link-admin` | Per-realm role required to access the dashboard. |

> **Note:** Admin Console settings always override environment variables. The priority is: Admin Console value → environment variable → built-in default.

---

## REST API Reference

**Base URL:** `/realms/{realm}/mideye-magic-link`

For example:
- `mideye-test` realm → `/realms/mideye-test/mideye-magic-link/...`
- `company-a` realm → `/realms/company-a/mideye-magic-link/...`

All endpoints require authentication (cookie or Bearer token) and the `mideye-magic-link-admin` realm role (or `mideye-magic-link-master` in the master realm for cross-realm access).

### Dashboard

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/dashboard` | HTML dashboard page. Redirects to login if unauthenticated. |

### Events

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/events?limit=N` | Recent authentication events (JSON array). Default limit: 500. |
| `GET` | `/api/top-users?limit=N` | Top usernames by total attempts with counts. Default limit: 20. |

### Statistics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/stats` | Statistics: total attempts, success, rejected, timeout, error, no_phone, not_configured counts. |
| `POST` | `/api/stats/reset` | Reset all statistics counters to zero. |

### Configuration

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/config` | Current Magic Link configuration as JSON (API key is redacted). |

### Export

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/export/events?limit=N` | Export events as CSV with `Content-Disposition` header. |

### Error responses

| Status | Meaning |
|--------|---------|
| `302` | Not authenticated (dashboard only) — redirects to Keycloak login page |
| `401` | Not authenticated (API) — no valid session cookie or Bearer token |
| `403` | Authenticated but missing the required role (`mideye-magic-link-admin` or `mideye-magic-link-master`) |

---

## Deployment Guide

### Build

```bash
cd spi
mvn clean package
```

The JAR is built at `spi/target/mideye-magic-link-authenticator.jar`.

### Deploy to Keycloak

Copy the JAR to Keycloak's providers directory:

```bash
cp spi/target/mideye-magic-link-authenticator.jar providers/
```

In Docker Compose, the `providers/` directory is mounted read-only:

```yaml
volumes:
  - ./providers:/opt/keycloak/providers:ro
```

Restart Keycloak to load the new SPI:

```bash
docker compose restart keycloak
```

### Post-deployment checklist

1. ✅ Verify SPI loaded: `docker compose logs keycloak | grep -i "magic\|mideye\|error"`
2. ✅ Configure Mideye Server URL and API key (Admin Console or env vars)
3. ✅ Create the authentication flow with "Mideye Magic Link" step (see main README, step 5)
4. ✅ Bind the flow to the browser
5. ✅ Register the `phoneNumber` attribute in the User Profile (or import the realm export)
6. ✅ Set phone numbers on test users
7. ✅ Create the `mideye-magic-link-admin` realm role (see main README, step 8)
8. ✅ Assign the role to admin users
9. ✅ Test dashboard access: `http://<host>/realms/<realm>/mideye-magic-link/dashboard`
10. ✅ Test login with MFA

---

## Troubleshooting

### "Access Denied" on the dashboard

**Cause:** Your user does not have the required role.

**Fix:**
1. Log in to the Keycloak Admin Console
2. Go to **Realm roles** → verify `mideye-magic-link-admin` exists in that realm
3. Go to **Users** → your user → **Role mapping** → assign `mideye-magic-link-admin`
4. Alternatively, in the **master** realm, assign the `mideye-magic-link-master` role for cross-realm access
5. Log out and back in to the dashboard

### Redirected to login in a loop

**Cause:** The `security-admin-console` client may not have the dashboard URL as a valid redirect URI.

**Fix:** In the Admin Console, go to **Clients** → `security-admin-console` → **Settings** → **Valid redirect URIs** and add `*` (for development) or the specific dashboard URL pattern `*/mideye-magic-link/dashboard`.

### API returns 401 with Bearer token

**Cause:** The token may be expired, or issued for a different realm.

**Fix:**
- Verify the token is issued for the correct realm
- Request a fresh token
- Ensure the user has the `mideye-magic-link-admin` role in that realm (or `mideye-magic-link-master` in the master realm)

### Authentication succeeds without MFA

**Cause:** The Mideye Magic Link step is not in the active authentication flow, or the flow is not bound to the browser.

**Fix:**
1. Verify the flow has the "Mideye Magic Link" step set to **Required**
2. Verify the flow is bound as the **Browser flow** (Authentication → ⋮ → Bind flow)

### "Not configured" events in dashboard

**Cause:** The Mideye Server URL or API key is not set.

**Fix:** Configure them in the Admin Console (Authentication → Flows → ⚙ Settings) or via environment variables (`MIDEYE_URL`, `MIDEYE_API_KEY`).

### Event log shows "⚠ In-memory cache" warning

**Expected behavior.** The event cache is stored in a `ConcurrentHashMap` in JVM memory. Data is lost on Keycloak restart. This is an acceptable trade-off for the example integration — a production deployment could extend this to persist events to a database.
