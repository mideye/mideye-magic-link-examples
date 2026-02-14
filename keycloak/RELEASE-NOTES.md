# Release Notes — Mideye Magic Link for Keycloak

## 2.0.0 — 2025-02-14

Complete overhaul: Admin Console GUI configuration replaces environment variables, per-realm config, per-realm event cache with dashboard, and comprehensive test suite.

---

### Admin Console GUI Configuration (BREAKING CHANGE)

- All settings are now configurable **per-realm** via the Keycloak Admin Console (Authentication → Flows → Mideye Magic Link → ⚙ Settings) — **no environment variables or Keycloak redeployment needed**
- 8 configurable properties exposed in the Admin Console:
  - **Mideye Server URL** (`mideye.url`) — base URL of the Mideye Server
  - **API Key** (`mideye.apiKey`) — Magic Link API key (password-masked in the UI)
  - **Phone number attribute** (`mideye.phoneAttribute`) — user attribute for phone number (default: `phoneNumber`)
  - **API timeout (seconds)** (`mideye.timeoutSeconds`) — how long to wait for user response (default: 120)
  - **Skip TLS verification** (`mideye.skipTlsVerify`) — disable cert verification for testing (default: false)
  - **Event log max size** (`mideye.eventLogMaxSize`) — max events in memory per realm (default: 1000)
  - **Event TTL (hours)** (`mideye.eventTtlHours`) — event expiration time (default: 1)
  - **Dashboard role** (`mideye.dashboardRole`) — realm role for dashboard access (default: `mideye-magic-link-admin`)
- Environment variables (`MIDEYE_URL`, `MIDEYE_API_KEY`) remain supported as **fallback** for backward compatibility — Admin Console values take precedence

### Per-Realm Architecture

- **MagicLinkEventCache** is now **per-realm** — each realm maintains its own independent event log and statistics counters
  - `MagicLinkEventCache.getInstance(realmId)` replaces the old singleton
  - Realms do not share event data
  - `removeInstance(realmId)` for cleanup
- **MagicLinkConfig** loads settings from the current realm's `AuthenticatorConfigModel`
- Each realm can have different Mideye Server credentials and settings configured independently

### Magic Link Dashboard (NEW)

- Built-in web dashboard for monitoring Magic Link authentication events
- Dashboard URL: `/realms/{realm}/mideye-magic-link/dashboard`
- **Dashboard authentication & authorization:**
  - Cookie-based session (browser) + Bearer token (API)
  - Per-realm role: `mideye-magic-link-admin` (configurable via `mideye.dashboardRole`)
  - Master realm cross-realm role: `mideye-magic-link-master`
  - Styled 403 "Access Denied" page for unauthorized users
  - Automatic redirect to Keycloak login for unauthenticated browser users
- **Dashboard features:**
  - Dark and light themes with OS preference detection and localStorage persistence
  - Statistics panel: total attempts, success, rejected, timeout, error, no_phone, not_configured
  - Event log table with search and outcome filter
  - Top users ranking
  - Configuration display (API key redacted)
  - CSV export (events)
  - Stats reset
  - Auto-refresh every 10 seconds
  - In-memory cache warning banner
  - XSS protection — all user data escaped before rendering

### REST API (NEW)

- `GET /dashboard` — HTML dashboard page (redirects to login if unauthenticated)
- `GET /api/events?limit=N` — recent authentication events (JSON)
- `GET /api/stats` — statistics counters (JSON)
- `POST /api/stats/reset` — reset all counters
- `GET /api/config` — current configuration (API key redacted)
- `GET /api/top-users?limit=N` — top usernames by attempt count
- `GET /api/export/events?limit=N` — export events as CSV

### Authentication Enhancements

- Events are now recorded in the per-realm cache with:
  - Username, masked phone number, outcome, duration (ms), timestamp
  - Outcome classification: `success`, `rejected`, `timeout`, `error`, `no_phone`, `not_configured`
- Phone numbers are masked (e.g., `***4567`) — full numbers are never stored in the event cache
- Duration tracking: time from API call start to response

### Removed: Environment Variable Configuration

- The following environment variables are **no longer the primary configuration method** (but still work as fallback):
  - `MIDEYE_URL` → use Admin Console "Mideye Server URL"
  - `MIDEYE_API_KEY` → use Admin Console "API Key"
- The following environment variables are **removed entirely** (no equivalent — now in Admin Console):
  - `MIDEYE_PHONE_ATTRIBUTE` → use Admin Console "Phone number attribute"
  - `MIDEYE_TIMEOUT_SECONDS` → use Admin Console "API timeout (seconds)"
  - `MIDEYE_SKIP_TLS_VERIFY` → use Admin Console "Skip TLS verification"

### New Source Files

| File | Purpose |
|------|---------|
| `MagicLinkConfig.java` | Configuration holder with 8 keys, `fromMap()` parser, env var fallback |
| `MagicLinkEventCache.java` | Per-realm in-memory ring buffer with stats, TTL pruning, top-user tracking |
| `MagicLinkDashboardResource.java` | REST resource: dashboard, events, stats, config, top-users, CSV export |
| `MagicLinkDashboardResourceFactory.java` | `RealmResourceProviderFactory` with ID `mideye-magic-link` |
| `magic-link-dashboard.html` | Single-page dashboard with dark/light theme |

### Updated Source Files

| File | Changes |
|------|---------|
| `MideyeMagicLinkAuthenticator.java` | Fully rewritten: reads from `AuthenticatorConfigModel`, records events to cache, tracks duration, classifies outcomes |
| `MideyeMagicLinkAuthenticatorFactory.java` | Fully rewritten: `isConfigurable()=true`, 8 `ProviderConfigProperty` entries via `ProviderConfigurationBuilder` |
| `pom.xml` | Added JUnit 5 + Surefire plugin for testing |

### Service Descriptors

- New: `META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory` → registers `MagicLinkDashboardResourceFactory`
- Existing: `META-INF/services/org.keycloak.authentication.AuthenticatorFactory` → registers `MideyeMagicLinkAuthenticatorFactory`

### Test Suite (NEW)

- **71 JUnit 5 tests**, all passing
- Test files:
  - `MagicLinkConfigTest.java` (20 tests) — defaults, `fromMap` parsing, null/empty/invalid input, boolean/int parsing, env var fallback, getters/setters
  - `MagicLinkEventCacheTest.java` (26 tests) — per-realm instances, event recording, ring buffer overflow, TTL pruning, statistics counters, top users, stats reset, event log clear, thread safety
  - `MideyeMagicLinkAuthenticatorTest.java` (14 tests) — config loading, phone number extraction, outcome classification, event recording
  - `MagicLinkDashboardResourceTest.java` (11 tests) — authorization checks, API responses, config endpoint, stats endpoint

### Migration from 1.x

1. **Rebuild and deploy** the updated JAR (`mvn clean package`)
2. **Configure via Admin Console** — go to Authentication → Flows → ⚙ Settings on the Mideye Magic Link step and enter the Mideye Server URL and API key
3. **Create dashboard role** — create the `mideye-magic-link-admin` realm role and assign it to admin users
4. **(Optional)** Keep `MIDEYE_URL` and `MIDEYE_API_KEY` in `docker-compose.yml` as fallback
5. **(Optional)** Remove no-longer-used env vars: `MIDEYE_PHONE_ATTRIBUTE`, `MIDEYE_TIMEOUT_SECONDS`, `MIDEYE_SKIP_TLS_VERIFY`
6. **Restart Keycloak** to load the new SPI

---

## 1.0.0 — 2025-02-10

Initial release of the Mideye Magic Link authenticator for Keycloak.

---

### Keycloak SPIs

- Single Keycloak Authenticator SPI deployed as a JAR (`mideye-magic-link-authenticator.jar`)
- Registered via `META-INF/services` (auto-discovered by Keycloak)
- Compatible with Keycloak 26.0.7
- Java 17, Maven build

### Magic Link Authentication

- Calls the Mideye Server Magic Link API (`GET /api/sfwa/auth?msisdn={phone}`) as a second-factor after password verification
- Supports push notifications (Mideye+) and SMS OTP
- Reads the user's phone number from a configurable user attribute (default: `phoneNumber`)
- Parses the JSON response — `TOUCH_ACCEPTED` = success, all other codes = failure
- Configurable API timeout (default: 120 seconds)
- Optional TLS certificate verification skip for development

### Configuration

- All settings via environment variables:
  - `MIDEYE_URL` — Mideye Server base URL (required)
  - `MIDEYE_API_KEY` — Magic Link API key (required)
  - `MIDEYE_PHONE_ATTRIBUTE` — user attribute name (default: `phoneNumber`)
  - `MIDEYE_TIMEOUT_SECONDS` — API timeout (default: 120)
  - `MIDEYE_SKIP_TLS_VERIFY` — skip TLS verification (default: false)

### Docker Compose Setup

- `docker-compose.yml` with PostgreSQL 16 + Keycloak 26.0.7
- PostgreSQL health check for startup ordering
- Mideye environment variables passed through from `.env`
- Provider JAR mounted read-only at `/opt/keycloak/providers`
- `.env.example` with documented credentials template

### Test Realm (realm-export.json)

- Pre-configured realm: `mideye-test`
- Test user: `testuser` / `testpassword` with phone number `+46701234567`
- OIDC public client: `test-app`
- `phoneNumber` attribute registered in User Profile

### Test Application (test-app/index.html)

- Standalone HTML test page for OIDC Authorization Code + PKCE flow
- Displays decoded ID token claims after authentication

### Architecture Decisions

- **No JSON library** — minimal hand-written JSON parser avoids classloader conflicts with Keycloak
- **Blocking API call** — the Magic Link API is synchronous (blocks until user responds), matching the Keycloak authenticator lifecycle
- **Phone number from user attribute** — follows Keycloak's standard user attribute model, not custom storage
