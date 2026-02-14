# Keycloak + Mideye Magic Link MFA Integration# Keycloak + Mideye Magic Link Integration# Keycloak + Mideye Server Integration Test Environment



Add **Mideye MFA** (push + SMS) to Keycloak as a second-factor authentication step using the **Mideye Magic Link API** — with a built-in **web dashboard** for monitoring and a fully **admin-configurable** setup per realm (no environment variables required).



## How it worksAdd Mideye MFA to Keycloak using the **Magic Link API** — a custom Keycloak authenticator (Java SPI) that calls the Mideye Server HTTP API directly.This directory contains everything needed to run Keycloak in Docker and integrate it with Mideye Server for MFA authentication.



```

┌─────────────┐  OIDC  ┌──────────────────┐  Magic Link API  ┌──────────────────┐

│  Web App    │ ──────► │   Keycloak       │ ────────────────► │  Mideye Server   │## How it works## Architecture

│  (browser)  │ ◄────── │   + Magic Link   │ ◄──────────────── │  (push / SMS)    │

└─────────────┘ tokens  │   SPI            │                   └──────────────────┘

                        └──────────────────┘                          │

                               │                                      ▼``````

                               ▼                              ┌──────────────────┐

                        ┌──────────────────┐                  │  User's phone    │User                 Keycloak                    Mideye Server┌─────────────────┐      OIDC        ┌─────────────────┐     MFA (HTTPS     ┌─────────────────┐

                        │  PostgreSQL       │                  │  (Mideye+ / SMS) │

                        └──────────────────┘                  └──────────────────┘ │                      │                             ││                 │  ─────────────►  │                 │     or RADIUS)     │                 │

```

 │  username/password   │                             ││   Web App       │                  │   Keycloak      │  ─────────────►   │  Mideye Server  │

### Authentication flow

 │─────────────────────►│                             ││   (browser)     │  ◄─────────────  │   (Docker)      │  ◄─────────────   │  (your server)  │

1. User submits username/password to Keycloak

2. After password verification, the **Mideye Magic Link** authenticator runs: │                      │  GET /api/sfwa/auth?msisdn= ││                 │   ID Token       │   :8080         │   Accept/Reject   │                 │

   - Reads the user's `phoneNumber` attribute

   - Calls Mideye Server: `GET /api/sfwa/auth?msisdn={phone}` with the `api-key` header │                      │────────────────────────────►│└─────────────────┘                  └─────────────────┘                   └─────────────────┘

   - If the user has **Mideye+** → a push notification appears on their phone

   - If SMS → an OTP is sent via SMS │                      │                             │── push/SMS to user's phone                                            │

3. The API call **blocks** until the user responds or the timeout expires

4. If `TOUCH_ACCEPTED` → authentication succeeds (`context.success()`) │                      │                             │                                            ▼

5. If rejected, timed out, or error → authentication fails (`context.failure()`)

6. Every authentication attempt is recorded in the **per-realm event cache** for the dashboard │         (user responds on phone)                   │                                     ┌─────────────────┐



### Components │                      │                             │                                     │  PostgreSQL     │



| Component | Type | Purpose | │                      │  {"code":"TOUCH_ACCEPTED"}  │                                     │  (Docker)       │

|-----------|------|---------|

| `MideyeMagicLinkAuthenticator` | Authenticator SPI | Calls the Mideye Magic Link API during login | │                      │◄────────────────────────────│                                     │  :5432          │

| `MideyeMagicLinkAuthenticatorFactory` | Authenticator Factory | Admin-configurable properties (8 settings) |

| `MagicLinkConfig` | Configuration | Per-realm config from Admin Console with env var fallback | │   OIDC tokens        │                             │                                     └─────────────────┘

| `MagicLinkEventCache` | Cache | Per-realm in-memory event log with statistics |

| `MagicLinkDashboardResource` | REST Resource SPI | Web dashboard + REST API for monitoring | │◄─────────────────────│                             │```

| `MagicLinkDashboardResourceFactory` | Resource Factory | Registers the dashboard at `/mideye-magic-link/` |

```

### Authentication outcome mapping

**How it works:**

| Mideye API response | Outcome | Notes |

|---------------------|---------|-------|1. User enters username + password in Keycloak

| `TOUCH_ACCEPTED` | `success` | User approved on phone |

| `TOUCH_REJECTED` | `rejected` | User denied on phone |2. After password verification, the custom authenticator calls the Magic Link API1. User opens a web application and clicks "Sign in"

| Timeout (no response) | `timeout` | User didn't respond within timeout period |

| HTTP error / exception | `error` | API unreachable, invalid credentials, etc. |3. Mideye Server sends a push notification (Mideye+) or SMS OTP to the user's phone2. Keycloak shows the login page (username + password)

| No phone number on user | `no_phone` | User attribute missing or empty |

| Config missing (no URL/key) | `not_configured` | Mideye Server URL or API key not set |4. The API blocks until the user responds3. After password verification, Keycloak triggers MFA via Mideye Server



## Prerequisites5. If `{"code":"TOUCH_ACCEPTED"}` → Keycloak completes login and issues OIDC tokens4. Mideye Server sends a push notification (Mideye+) or SMS OTP to the user's phone



- **Docker** and **Docker Compose** installed5. User responds → Mideye accepts/rejects → Keycloak completes login → user gets OIDC tokens

- **Java 17+** and **Maven 3.8+** (to build the SPI JAR)

- **Mideye Server** with the Magic Link API enabled and an API key## Prerequisites



---## Prerequisites



## 1. Configure Mideye Server credentials- **Docker** and **Docker Compose** installed



Copy the example environment file:- **Mideye Server** running and accessible from the Docker host- **Docker** and **Docker Compose** installed



```bash- A user provisioned in Mideye Server with a phone number- **Mideye Server** running and accessible from the Docker host

cp .env.example .env

```- A **Magic Link endpoint** configured in Mideye Server with an API key (see [Magic Link API docs](https://www.mideye.com/docs/integrations/api/magic-link-api/))- A user provisioned in Mideye Server with a phone number



Edit `.env` with your Mideye Server credentials:



```bash------

MIDEYE_URL=https://mideye.example.com:8443

MIDEYE_API_KEY=your-api-key-here

```

## 1. Configure Mideye Server connection## 1. Start Keycloak

> **Note:** Environment variables are the **fallback** method. You can also configure these directly in the Keycloak Admin Console (see step 6). Admin Console values take precedence.



## 2. Build the SPI JAR

Copy the example environment file and fill in your values:Edit `docker-compose.yml` and set your Mideye Server connection:

```bash

cd spi

mvn clean package

cp target/mideye-magic-link-authenticator.jar ../providers/```bash```yaml

cd ..

```cp .env.example .envMIDEYE_URL: https://your-mideye-server:8443



## 3. Start Keycloak```MIDEYE_API_KEY: your-api-key-here



```bash```

docker compose up -d

```Edit `.env`:



This starts:Then start:

- **Keycloak** on http://localhost:8080 (admin/admin)

- **PostgreSQL** as the backing database```bash



Wait ~30 seconds, then verify:MIDEYE_URL=https://your-mideye-server:8443```bash



```bashMIDEYE_API_KEY=your-api-key-heredocker compose up -d

docker compose logs -f keycloak

`````````



Look for: `Keycloak 26.0.7 on JVM (powered by Quarkus) started`



## 4. Import the test realmOr edit `docker-compose.yml` directly — update the `MIDEYE_URL` and `MIDEYE_API_KEY` environment variables under the `keycloak` service.This starts:



1. Open http://localhost:8080/admin and log in with `admin` / `admin`- **Keycloak** on http://localhost:8080 (admin/admin)

2. Click **Keycloak** (top-left) → **Create Realm**

3. Click **Browse...** and upload `realm-export.json`---- **PostgreSQL** as the backing database

4. Click **Create**



This creates:

- Realm: `mideye-test`## 2. Start KeycloakWait ~30 seconds, then verify Keycloak is running:

- User: `testuser` / `testpassword` (with phone number `+46701234567`)

- Client: `test-app` (OIDC public client)



## 5. Create the authentication flow```bash```bash



1. In Keycloak Admin Console → make sure you're in the **mideye-test** realmdocker compose up -ddocker compose logs -f keycloak

2. Go to **Authentication** (left sidebar)

3. Click **Create flow** (top right)``````

4. Fill in:

   - **Name:** `Mideye MFA Browser`

   - **Flow type:** `Basic flow`

   - **Description:** `Username/password + Mideye Magic Link MFA`This starts:Look for: `Keycloak 26.0.7 on JVM (powered by Quarkus) started`

5. Click **Create**

- **Keycloak** on http://localhost:8080 (admin/admin)

### Add the username/password step

- **PostgreSQL** as the backing database---

1. Click **Add execution**

2. Search for **Username Password Form**

3. Select it and click **Add**

4. Set its requirement to **Required**Wait ~30 seconds, then verify Keycloak is running:## 2. Log in to Keycloak Admin Console



### Add the Mideye MFA step



1. Click **Add execution** again```bashOpen http://localhost:8080/admin and log in:

2. Search for **Mideye Magic Link**

3. Select it and click **Add**docker compose logs -f keycloak- **Username:** `admin`

4. Set its requirement to **Required**

```- **Password:** `admin`

> **"Mideye Magic Link" not in the list?** The JAR wasn't loaded. Check `docker compose logs keycloak` for errors and make sure you restarted Keycloak after copying the JAR to `providers/`.



The flow should now look like this:

Look for: `Keycloak 26.0.7 on JVM (powered by Quarkus) started`---

| Step | Type | Requirement |

|------|------|-------------|

| Username Password Form | execution | **Required** |

| Mideye Magic Link | execution | **Required** |---## 3. Create the Realm



### Bind the flow to the browser



1. Go back to **Authentication** (left sidebar) to see the list of all flows## 3. Create the realmYou have two options:

2. Find the **Mideye MFA Browser** flow in the list

3. Click the **⋮** (three dots) menu on that row → **Bind flow**

4. Select **Browser flow**

5. Click **Save**Open http://localhost:8080/admin and log in with `admin` / `admin`.### Option A: Import from file (recommended)



## 6. Configure settings in Admin Console



The Magic Link authenticator is **configurable** directly from the Keycloak Admin Console — no environment variable changes or restarts needed.### Option A: Import from file (recommended)1. In the top-left corner, click **Keycloak** → **Create Realm**



1. Go to **Authentication** in the left sidebar2. Click **Browse...** and upload `realm-export.json` from this directory

2. Click on the **Mideye MFA Browser** flow

3. Click the **⚙ gear icon** next to "Mideye Magic Link"1. In the top-left corner, click **Keycloak** → **Create Realm**3. Click **Create**

4. Set your values:

2. Click **Browse...** and upload `realm-export.json` from this directory

| Setting | Config key | Default | Description |

|---------|-----------|---------|-------------|3. Click **Create**This creates:

| **Mideye Server URL** | `mideye.url` | *(env var)* | Base URL of the Mideye Server (e.g., `https://mideye.example.com:8443`) |

| **API Key** | `mideye.apiKey` | *(env var)* | API key for the Magic Link endpoint (masked) |- Realm: `mideye-test`

| **Phone number attribute** | `mideye.phoneAttribute` | `phoneNumber` | Keycloak user attribute containing the phone number in E.164 format |

| **API timeout (seconds)** | `mideye.timeoutSeconds` | `120` | How long to wait for the user to respond (the API call blocks) |This creates:- User Profile: `phoneNumber` attribute registered (so it appears on the user Attributes tab)

| **Skip TLS verification** | `mideye.skipTlsVerify` | `false` | Skip TLS cert verification — **never in production** |

| **Event log max size** | `mideye.eventLogMaxSize` | `1000` | Maximum events in the in-memory event log per realm |- Realm: `mideye-test`- User: `testuser` / `testpassword` (phone: `+46701234567`)

| **Event TTL (hours)** | `mideye.eventTtlHours` | `1` | Events older than this are pruned |

| **Dashboard role** | `mideye.dashboardRole` | `mideye-magic-link-admin` | Realm role required for dashboard access |- User Profile: `phoneNumber` attribute registered (so it appears on the user Attributes tab)- Client: `test-app` (OIDC, redirect to `http://localhost:3000/*`)



5. Click **Save**- User: `testuser` / `testpassword` (phone: `+46701234567`)



> **Tip:** Admin Console values take precedence over environment variables. If you leave a field empty, the corresponding env var (`MIDEYE_URL`, `MIDEYE_API_KEY`) is used as fallback.- Client: `test-app` (OIDC, redirect to `http://localhost:3000/*`)> **After import:** Go to **Users** → `testuser` → **Attributes** and change `phoneNumber` to a real phone number registered in your Mideye Server.



## 7. Verify the user has a phone number



The authenticator looks up the user's phone number from the `phoneNumber` attribute.> **After import:** Go to **Users** → `testuser` → **Attributes** and change `phoneNumber` to a real phone number registered in your Mideye Server.### Option B: Create manually



1. Go to **Users** → click on your test user

2. Go to the **Attributes** tab

3. Verify there's a **Phone number** field with a valid phone number (e.g. `+46701234567`)### Option B: Create manually1. Click **Create Realm** → name it `mideye-test` → **Create**

4. This phone number must match a user provisioned in Mideye Server

2. Register the `phoneNumber` attribute: see [Register the phoneNumber attribute](#register-the-phonenumber-attribute)

> If you imported `realm-export.json`, the test user already has `+46701234567`. Change this to a real phone number.

1. Click **Create Realm** → name it `mideye-test` → **Create**3. Create user: see [Create a Test User](#create-a-test-user)

> **Don't see the Phone number field?** You need to register the `phoneNumber` attribute in the User Profile. See [Register the phoneNumber attribute](#register-the-phonenumber-attribute).

2. Register the `phoneNumber` attribute (see [Register the phoneNumber attribute](#register-the-phonenumber-attribute) below)4. Create client: see [Create a Test Client](#create-a-test-client)

---

3. Create a test user (see [Create a test user](#create-a-test-user) below)

## 8. Magic Link Dashboard

---

The SPI includes a built-in web dashboard for monitoring Magic Link authentication events.

---

### Dashboard authentication & authorization

## 4. Choose Integration Path

> **⚠️ Security:** All dashboard and API endpoints require authentication and authorization. This is enforced at the SPI level — no external proxy or nginx rules are required.

## 4. Deploy the authenticator JAR

The dashboard uses two layers of access control:

There are two ways to connect Keycloak to Mideye Server for MFA:

1. **Authentication** — the user must have a valid Keycloak session. Browser users are automatically redirected to the Keycloak login page. API callers must provide a Bearer token.

2. **Authorization** — the authenticated user must have the **`mideye-magic-link-admin`** realm role (or the **`mideye-magic-link-master`** role in the master realm for cross-realm access). Users without either role see a styled "Access Denied" page.The pre-built JAR is in `providers/`. It's already mounted into the Keycloak container via `docker-compose.yml`.



### Set up the `mideye-magic-link-admin` role| Path | Protocol | Guide |



Before anyone can access the dashboard, you must create the role and assign it:Restart Keycloak to load the provider:|------|----------|-------|



1. In the Keycloak Admin Console, go to **Realm roles** (left sidebar)| **Magic Link API** (recommended) | HTTPS (port 8443) | [MAGIC-LINK.md](MAGIC-LINK.md) |

2. Click **Create role**

3. Name: `mideye-magic-link-admin` → Click **Save**```bash| **RADIUS** | UDP (port 1812) | [RADIUS.md](RADIUS.md) |

4. Go to **Users** → select the admin user → **Role mapping** tab

5. Click **Assign role** → select `mideye-magic-link-admin` → **Assign**docker compose restart keycloak



### (Optional) Set up `mideye-magic-link-master` for cross-realm access```### Which one should I use?



To allow a master realm user to access the Magic Link dashboard in any realm:



1. Switch to the **master** realm in the Admin ConsoleVerify the provider loaded — check the logs for any errors:- **Magic Link API** — Simpler setup. Uses a custom Keycloak authenticator (Java SPI) that calls the Mideye HTTP API directly. Supports push (Mideye+) and SMS. No RADIUS configuration needed.

2. Go to **Realm roles** → **Create role** → Name: `mideye-magic-link-master` → **Save**

3. Go to **Users** → select the user → **Role mapping** → Assign `mideye-magic-link-master`- **RADIUS** — Traditional approach. Requires a third-party Keycloak RADIUS plugin and RADIUS client configuration in Mideye Server. Supports all Mideye auth types (OTP, Touch, Challenge-Response).



> **Tip:** You can use a different per-realm role name by setting **Dashboard role** in the Admin Console (Authentication → Flows → ⚙ Settings).```bash



### Access the dashboarddocker compose logs keycloak | grep -i "mideye\|provider\|error"---



Each realm has its **own** Magic Link dashboard. The URL pattern is:```



```## Register the phoneNumber attribute

http://<keycloak-host>/realms/<realm>/mideye-magic-link/dashboard

```> **Building from source (optional):** If you need to rebuild the JAR, you need Java 17+ and Maven:



**Example — multi-realm setup:**> ```bash> Skip this if you imported `realm-export.json` — the attribute is already registered.



| Realm | Dashboard URL |> cd spi

|-------|---------------|

| `company-a` | `https://keycloak.example.com/realms/company-a/mideye-magic-link/dashboard` |> mvn clean packageKeycloak 26 requires custom attributes to be registered in the **User Profile** schema before they appear on the user's Attributes tab.

| `company-b` | `https://keycloak.example.com/realms/company-b/mideye-magic-link/dashboard` |

| `mideye-test` | `http://localhost:8080/realms/mideye-test/mideye-magic-link/dashboard` |> cp target/mideye-magic-link-authenticator.jar ../providers/



Each dashboard is isolated — it shows only that realm's events, statistics, and configuration.> docker compose restart keycloak1. In the left sidebar, go to **Realm settings**



If you are not logged in, you will be redirected to the Keycloak login page. After authenticating, the dashboard loads automatically.> ```2. Click the **User profile** tab



> **Tip:** A user with the `mideye-magic-link-master` role in the master realm can access **any** realm's dashboard by navigating to that realm's URL.3. Click the **JSON editor** sub-tab



### Dashboard features---4. In the `"attributes"` array, add the following entry after the `lastName` attribute:



- **Dark and light themes** — toggle button with OS preference detection

- **Statistics panel** — total attempts, success, rejected, timeout, error, no_phone, not_configured

- **Event log** — real-time authentication events with search and filter (username, outcome)## 5. Create the authentication flow```json

- **Top users** — most frequently seen usernames with attempt counts

- **Configuration display** — current settings (API URL masked, phone attribute, timeout, TLS, etc.){

- **CSV export** — export events to CSV file

- **Auto-refresh** — dashboard refreshes every 10 seconds1. In Keycloak Admin Console → make sure you're in the **mideye-test** realm  "name": "phoneNumber",

- **In-memory warning** — banner reminding that event data is lost on Keycloak restart

2. Go to **Authentication** (left sidebar)  "displayName": "Phone number",

### Dashboard REST API

3. Click **Create flow** (top right)  "validations": {

The dashboard is backed by a REST API that can also be used programmatically. All API endpoints require authentication — use a Bearer token obtained from the Keycloak token endpoint.

4. Fill in:    "length": { "max": 255 }

**Obtaining a Bearer token:**

   - **Name:** `Mideye MFA Browser`  },

```bash

TOKEN=$(curl -s -X POST \   - **Flow type:** `Basic flow`  "permissions": {

  "http://localhost:8080/realms/mideye-test/protocol/openid-connect/token" \

  -H "Content-Type: application/x-www-form-urlencoded" \   - **Description:** `Username/password + Mideye Magic Link MFA`    "view": ["admin", "user"],

  -d "grant_type=password&client_id=security-admin-console&username=admin&password=admin" \

  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")5. Click **Create**    "edit": ["admin", "user"]

```

  },

**API calls:**

You'll see an empty flow:  "multivalued": false

```bash

# Recent authentication events}

curl -H "Authorization: Bearer $TOKEN" \

  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/events> *No steps — You can start defining this flow by adding a sub-flow or an execution*```



# Statistics

curl -H "Authorization: Bearer $TOKEN" \

  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/stats### Add the username/password step5. Click **Save**



# Reset statistics

curl -X POST -H "Authorization: Bearer $TOKEN" \

  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/stats/reset1. Click **Add execution**The `phoneNumber` field will now appear when editing a user's Attributes.



# Current configuration2. In the search field, search for **Username Password Form**

curl -H "Authorization: Bearer $TOKEN" \

  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/config3. Select it and click **Add**---



# Top users4. The step appears in the flow — set its requirement to **Required**

curl -H "Authorization: Bearer $TOKEN" \

  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/top-users## Create a Test User



# Export events as CSV### Add the Mideye MFA step

curl -H "Authorization: Bearer $TOKEN" \

  http://localhost:8080/realms/mideye-test/mideye-magic-link/api/export/events> Skip this if you imported `realm-export.json` — the user is already created.

```

1. Click **Add execution** again>

---

2. Search for **Mideye Magic Link**> Make sure you have [registered the phoneNumber attribute](#register-the-phonenumber-attribute) first.

## 9. Test the login

3. Select it and click **Add**

### Using the Keycloak Account Console

4. Set its requirement to **Required**1. In Keycloak Admin Console → **mideye-test** realm

Open: http://localhost:8080/realms/mideye-test/account

2. Go to **Users** → **Create new user**

1. Click **Sign in**

2. Enter `testuser` and the password> **"Mideye Magic Link" not in the list?** The JAR wasn't loaded. Check `docker compose logs keycloak` for errors and make sure you restarted Keycloak after copying the JAR to `providers/`.3. Fill in:

3. Wait — Mideye MFA is triggered:

   - If the user has Mideye+ → a push notification appears on their phone   - **Username:** `testuser`

   - If SMS → an OTP is sent

4. Respond on the phoneThe flow should now look like this:   - **Email:** your email

5. You should be redirected back and logged in

   - **First name / Last name:** your name

### Check Keycloak logs during authentication

| Step | Type | Requirement |4. Click **Create**

```bash

docker compose logs -f keycloak|------|------|-------------|5. Go to the **Credentials** tab → **Set password**

```

| Username Password Form | execution | **Required** |   - Enter a password

You should see:

| Mideye Magic Link | execution | **Required** |   - Toggle **Temporary** to **Off**

```

Mideye MFA: Initiating authentication for user 'testuser' (phone: ***4567)6. Go to the **Attributes** tab

Mideye MFA: Authentication ACCEPTED for user 'testuser'

```### Bind the flow to the browser   - Set **Phone number** to the user's phone number in E.164 format (e.g. `+46701234567`)



### Using the test HTML page7. Click **Save**



A minimal OIDC test page is included at `test-app/index.html`:1. Go back to **Authentication** (left sidebar) to see the list of all flows



```bash2. Find the **Mideye MFA Browser** flow in the list> **Important:** The phone number must match a user provisioned in Mideye Server.

cd test-app

python3 -m http.server 30003. Click the **⋮** (three dots) menu on that row → **Bind flow**

```

4. Select **Browser flow**## Create a Test Client

Open http://localhost:3000 and click **Sign in with Keycloak**.

5. Click **Save**

---

> Skip this if you imported `realm-export.json` — the client is already created.

## Stop / Clean up

---

```bash

# Stop containers (keep data)1. Go to **Clients** → **Create client**

docker compose down

## 6. Verify the user has a phone number2. Settings:

# Stop and remove ALL data (database, volumes)

docker compose down -v   - **Client type:** `OpenID Connect`

```

The authenticator looks up the user's phone number from the `phoneNumber` attribute.   - **Client ID:** `test-app`

---

3. Click **Next**

## Register the phoneNumber attribute

1. Go to **Users** → click on your test user4. Confirm **Standard flow** is enabled

> Skip this if you imported `realm-export.json` — the attribute is already registered.

2. Go to the **Attributes** tab5. Click **Next**

Keycloak 26 requires custom attributes to be registered in the **User Profile** schema before they appear on the user's Attributes tab.

3. Verify there's a **Phone number** field with a valid phone number (e.g. `+46701234567`)6. Set:

1. In the left sidebar, go to **Realm settings**

2. Click the **User profile** tab4. This phone number must match a user provisioned in Mideye Server   - **Valid redirect URIs:** `http://localhost:3000/*`

3. Click the **JSON editor** sub-tab

4. In the `"attributes"` array, add the following entry after the `lastName` attribute:   - **Web origins:** `http://localhost:3000`



```json> If you imported `realm-export.json`, the test user already has `+46701234567`. Change this to a real phone number.7. Click **Save**

{

  "name": "phoneNumber",

  "displayName": "Phone number",

  "validations": {> **Don't see the Phone number field?** You need to register the `phoneNumber` attribute in the User Profile first. See [Register the phoneNumber attribute](#register-the-phonenumber-attribute).---

    "length": { "max": 255 }

  },

  "permissions": {

    "view": ["admin", "user"],---## Test the Login Flow

    "edit": ["admin", "user"]

  },

  "multivalued": false

}## 7. Test the login### Using the Keycloak Account Console

```



5. Click **Save**

Open http://localhost:8080/realms/mideye-test/accountOpen: http://localhost:8080/realms/mideye-test/account

The `phoneNumber` field will now appear when editing a user's Attributes.



---

1. Click **Sign in**1. Click **Sign in**

## Troubleshooting

2. Enter the username and password2. Enter `testuser` and the password

### Keycloak won't start

3. Wait — Mideye MFA is triggered:3. Mideye MFA is triggered → respond on your phone

```bash

docker compose logs keycloak   - If the user has Mideye+ → a push notification appears on their phone4. You should be logged in to the Account Console

```

   - If SMS → an OTP is sent

Common issues:

- **Port 8080 already in use** → change the port mapping in `docker-compose.yml`4. Respond on the phone### Using the test HTML page

- **Database connection failed** → wait a few seconds (PostgreSQL may still be starting)

- **Provider JAR errors** → check the JAR was built for the correct Keycloak version5. You should be redirected back and logged in



### "Mideye Magic Link" not in the step listA minimal OIDC test page is included at `test-app/index.html`:



- Verify the JAR is in `providers/`:### Check Keycloak logs during authentication

  ```bash

  ls -la providers/```bash

  ```

- Restart Keycloak: `docker compose restart keycloak````bashcd test-app

- Check logs: `docker compose logs keycloak | grep -i error`

docker compose logs -f keycloakpython3 -m http.server 3000

### Authentication fails immediately

``````

- Check the settings in the Admin Console (Authentication → Flows → ⚙ Settings)

- Verify Mideye Server is reachable from the container:

  ```bash

  docker compose exec keycloak curl -k https://your-mideye-server:8443/api/sfwa/authYou should see:Open http://localhost:3000 and click **Sign in with Keycloak**.

  ```

  (This should return a 400 or similar — the point is to verify connectivity)```



### "No phone number" error in logsMideye MFA: Initiating authentication for user 'testuser' (phone: ***4567)---



- Verify the `phoneNumber` attribute is registered in the User Profile (see [Register the phoneNumber attribute](#register-the-phonenumber-attribute))Mideye MFA: Authentication ACCEPTED for user 'testuser'

- Verify the user has a value set in the **Phone number** field on the Attributes tab

- Check the attribute name matches the **Phone number attribute** setting (default: `phoneNumber`)```## Stop / Clean Up



### TLS certificate errors



If Mideye Server uses a self-signed certificate, set **Skip TLS verification** to `true` in the Admin Console:---```bash



> **Warning:** Never use this in production. Import the Mideye Server CA certificate into the Keycloak container's truststore instead.# Stop containers (keep data)



### Timeout (user never gets prompted)## 8. Test with the OIDC test page (optional)docker compose down



- Verify the phone number in Keycloak matches a user in Mideye Server

- Check Mideye Server logs for the authentication request

- Verify the API key is validA minimal OIDC test page is included at `test-app/index.html` — it shows the full Authorization Code + PKCE flow and displays the decoded ID token claims.# Stop and remove ALL data (database, volumes)

- Check the **API timeout** setting (default: 120 seconds)

docker compose down -v

### Docker networking

### Create the OIDC client in Keycloak```

If Mideye Server runs on the Docker host machine:

- **macOS / Windows:** Use `host.docker.internal` as the server address

- **Linux:** Add `extra_hosts: ["host.docker.internal:host-gateway"]` to `docker-compose.yml` or use the host's actual IP address

> Skip this if you imported `realm-export.json` — the `test-app` client is already created.---

---



## How the authenticator works (technical)

1. In Keycloak Admin Console → **mideye-test** realm## Troubleshooting

The authenticator is a standard [Keycloak Authentication SPI](https://www.keycloak.org/docs/latest/server_development/#_auth_spi):

2. Go to **Clients** (left sidebar) → **Create client**

- **`MideyeMagicLinkAuthenticator.java`** — The authenticator logic. After password auth succeeds, it:

  1. Loads per-realm configuration from the Admin Console (`AuthenticatorConfigModel`)3. **General settings:**### Keycloak won't start

  2. Reads the user's `phoneNumber` attribute

  3. Makes an HTTP GET to `{mideyeUrl}/api/sfwa/auth?msisdn={phone}` with the `api-key` header   - **Client type:** `OpenID Connect`

  4. Parses the JSON response code

  5. Records the event in the per-realm `MagicLinkEventCache` (with duration, outcome, masked phone)   - **Client ID:** `test-app````bash

  6. Calls `context.success()` if `TOUCH_ACCEPTED`, otherwise `context.failure()`

4. Click **Next**docker compose logs keycloak

- **`MideyeMagicLinkAuthenticatorFactory.java`** — Registers the authenticator with Keycloak as "Mideye Magic Link" and exposes 8 configurable properties in the Admin Console

5. **Capability config:**```

- **`MagicLinkConfig.java`** — Configuration holder that parses Admin Console settings with environment variable fallback

   - Confirm **Standard flow** is enabled

- **`MagicLinkEventCache.java`** — Per-realm in-memory ring buffer for recent authentication events, with statistics counters and TTL-based pruning

   - **Client authentication:** leave **Off** (this is a public client)Common issues:

- **`MagicLinkDashboardResource.java`** — REST resource providing the web dashboard, events API, stats API, config API, top-users API, CSV export, and stats reset

6. Click **Next**- **Port 8080 already in use** → change the port mapping in `docker-compose.yml`

- **`MagicLinkDashboardResourceFactory.java`** — `RealmResourceProviderFactory` that registers at `/mideye-magic-link/`

7. **Login settings:**- **Database connection failed** → wait a few seconds (PostgreSQL may still be starting)

- **`META-INF/services/`** — Java SPI service descriptors for auto-discovery by Keycloak

   - **Root URL:** `http://localhost:3000`- **Provider JAR errors** → check the JAR was built for the correct Keycloak version

Source code: [`spi/src/main/java/com/mideye/keycloak/`](spi/src/main/java/com/mideye/keycloak/)

   - **Valid redirect URIs:** `http://localhost:3000/*`

---

   - **Web origins:** `http://localhost:3000`### Docker networking

## File structure

8. Click **Save**

```

keycloak/If Mideye Server runs on the Docker host machine:

├── README.md                  ← This file (start here)

├── RELEASE-NOTES.md           ← Version history and changelogs### Start the test page- **macOS / Windows:** Use `host.docker.internal` as the server address

├── docs/

│   └── README.md              ← Technical documentation- **Linux:** Add `extra_hosts: ["host.docker.internal:host-gateway"]` to `docker-compose.yml` or use the host's actual IP address

├── docker-compose.yml         ← Keycloak + PostgreSQL

├── .env.example               ← Example environment variables (fallback only)```bash

├── realm-export.json          ← Pre-configured realm (import into Keycloak)

├── providers/                 ← Keycloak provider JARs (mounted into container)cd test-app---

│   └── mideye-magic-link-authenticator.jar

├── spi/                       ← Custom Mideye Magic Link authenticator sourcepython3 -m http.server 3000

│   ├── pom.xml

│   └── src/main/java/com/mideye/keycloak/```## File Structure

│       ├── MagicLinkConfig.java

│       ├── MagicLinkDashboardResource.java

│       ├── MagicLinkDashboardResourceFactory.java

│       ├── MagicLinkEventCache.java### Sign in```

│       ├── MideyeMagicLinkAuthenticator.java

│       └── MideyeMagicLinkAuthenticatorFactory.javakeycloak/

└── test-app/

    └── index.html             ← Minimal OIDC test page1. Open http://localhost:3000├── README.md                  ← This file (start here)

```

2. Verify the settings match your setup:├── MAGIC-LINK.md              ← Magic Link API integration guide

## References

   - **Keycloak URL:** `http://localhost:8080`├── RADIUS.md                  ← RADIUS integration guide

- [Keycloak Documentation](https://www.keycloak.org/documentation)

- [Keycloak Docker Getting Started](https://www.keycloak.org/getting-started/getting-started-docker)   - **Realm:** `mideye-test`├── docker-compose.yml         ← Keycloak + PostgreSQL

- [Mideye Magic Link API](https://www.mideye.com/docs/integrations/api/magic-link-api/)

- [Mideye OIDC Integration](https://www.mideye.com/docs/integrations/identity/oidc/)   - **Client ID:** `test-app`├── realm-export.json          ← Pre-configured realm (import into Keycloak)



---3. Click **Sign in with Keycloak**├── providers/                 ← Keycloak provider JARs (mounted into container)



## License4. You are redirected to Keycloak → enter username and password├── spi/                       ← Custom Mideye Magic Link authenticator SPI



MIT License — see [LICENSE](../LICENSE) for details.5. Mideye MFA is triggered → respond on your phone│   ├── pom.xml


6. You are redirected back to the test page and should see:│   └── src/main/java/com/mideye/keycloak/

   - ✅ **Authenticated** with a claims table (sub, username, email, name)│       ├── MideyeMagicLinkAuthenticator.java

   - The decoded **ID Token** (JSON)│       └── MideyeMagicLinkAuthenticatorFactory.java

   - The raw **Access Token**└── test-app/

    └── index.html             ← Minimal OIDC test page

---```



## Stop / Clean up## References



```bash- [Keycloak Documentation](https://www.keycloak.org/documentation)

# Stop containers (keep data)- [Keycloak Docker Getting Started](https://www.keycloak.org/getting-started/getting-started-docker)

docker compose down- [Mideye Magic Link API](https://www.mideye.com/docs/integrations/api/magic-link-api/)

- [Mideye OIDC Integration](https://www.mideye.com/docs/integrations/identity/oidc/)

# Stop and remove ALL data (database, volumes)
docker compose down -v
```

---

## Environment variables reference

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MIDEYE_URL` | **Yes** | — | Mideye Server base URL (e.g. `https://mideye.example.com:8443`) |
| `MIDEYE_API_KEY` | **Yes** | — | Magic Link API key from Mideye Server |
| `MIDEYE_PHONE_ATTRIBUTE` | No | `phoneNumber` | Keycloak user attribute containing the phone number |
| `MIDEYE_TIMEOUT_SECONDS` | No | `120` | How long to wait for user response (seconds) |
| `MIDEYE_SKIP_TLS_VERIFY` | No | `false` | Set to `true` to skip TLS cert verification (testing only!) |

---

## Register the phoneNumber attribute

> Skip this if you imported `realm-export.json` — the attribute is already registered.

Keycloak 26 requires custom attributes to be registered in the **User Profile** schema before they appear on the user's Attributes tab.

1. In the left sidebar, go to **Realm settings**
2. Click the **User profile** tab
3. Click the **JSON editor** sub-tab
4. In the `"attributes"` array, add the following entry after the `lastName` attribute:

```json
{
  "name": "phoneNumber",
  "displayName": "Phone number",
  "validations": {
    "length": { "max": 255 }
  },
  "permissions": {
    "view": ["admin", "user"],
    "edit": ["admin", "user"]
  },
  "multivalued": false
}
```

5. Click **Save**

The `phoneNumber` field will now appear when editing a user's Attributes.

---

## Create a test user

> Skip this if you imported `realm-export.json` — the user is already created.
>
> Make sure you have [registered the phoneNumber attribute](#register-the-phonenumber-attribute) first.

1. In Keycloak Admin Console → **mideye-test** realm
2. Go to **Users** → **Create new user**
3. Fill in:
   - **Username:** `testuser`
   - **Email:** your email
   - **First name / Last name:** your name
4. Click **Create**
5. Go to the **Credentials** tab → **Set password**
   - Enter a password
   - Toggle **Temporary** to **Off**
6. Go to the **Attributes** tab
   - Set **Phone number** to the user's phone number in E.164 format (e.g. `+46701234567`)
7. Click **Save**

> **Important:** The phone number must match a user provisioned in Mideye Server.

---

## Troubleshooting

### Keycloak won't start

```bash
docker compose logs keycloak
```

Common issues:
- **Port 8080 already in use** → change the port mapping in `docker-compose.yml`
- **Database connection failed** → wait a few seconds (PostgreSQL may still be starting)
- **Provider JAR errors** → check the JAR was built for the correct Keycloak version

### "Mideye Magic Link" not in the step list

- Verify the JAR is in `providers/`:
  ```bash
  ls -la providers/
  ```
- Restart Keycloak: `docker compose restart keycloak`
- Check logs: `docker compose logs keycloak | grep -i error`

### Authentication fails immediately

- Check the environment variables are set:
  ```bash
  docker compose exec keycloak env | grep MIDEYE
  ```
- Verify Mideye Server is reachable from the container:
  ```bash
  docker compose exec keycloak curl -k https://your-mideye-server:8443/api/sfwa/auth
  ```
  (This should return a 400 or similar — the point is to verify connectivity)

### "No phone number" error in logs

- Verify the `phoneNumber` attribute is registered in the User Profile (see [Register the phoneNumber attribute](#register-the-phonenumber-attribute))
- Verify the user has a value set in the **Phone number** field on the Attributes tab
- Check the attribute name matches `MIDEYE_PHONE_ATTRIBUTE` (default: `phoneNumber`)

### TLS certificate errors

If Mideye Server uses a self-signed certificate, set `MIDEYE_SKIP_TLS_VERIFY=true` in `docker-compose.yml`:

```yaml
MIDEYE_SKIP_TLS_VERIFY: "true"
```

> **Warning:** Never use this in production. Import the Mideye Server CA certificate into the Keycloak container's truststore instead.

### Timeout (user never gets prompted)

- Verify the phone number in Keycloak matches a user in Mideye Server
- Check Mideye Server logs for the authentication request
- Verify the API key is valid

### Docker networking

If Mideye Server runs on the Docker host machine:
- **macOS / Windows:** Use `host.docker.internal` as the server address
- **Linux:** Add `extra_hosts: ["host.docker.internal:host-gateway"]` to `docker-compose.yml` or use the host's actual IP address

### OIDC test page: "Client not found"

The `test-app` client doesn't exist in the `mideye-test` realm. Create it using the steps in [Test with the OIDC test page](#8-test-with-the-oidc-test-page-optional).

### OIDC test page: "Invalid redirect URI"

Make sure **Valid redirect URIs** is set to `http://localhost:3000/*` and that you're accessing the page at exactly `http://localhost:3000`.

### OIDC test page: CORS errors

Make sure **Web origins** is set to `http://localhost:3000` in the client settings.

---

## How the authenticator works (technical)

The authenticator is a standard [Keycloak Authentication SPI](https://www.keycloak.org/docs/latest/server_development/#_auth_spi):

- **`MideyeMagicLinkAuthenticator.java`** — The authenticator logic. After password auth succeeds, it:
  1. Reads the user's `phoneNumber` attribute
  2. Makes an HTTP GET to `{MIDEYE_URL}/api/sfwa/auth?msisdn={phone}` with the `api-key` header
  3. Parses the JSON response code
  4. Calls `context.success()` if `TOUCH_ACCEPTED`, otherwise `context.failure()`

- **`MideyeMagicLinkAuthenticatorFactory.java`** — Registers the authenticator with Keycloak as "Mideye Magic Link"

- **`META-INF/services/org.keycloak.authentication.AuthenticatorFactory`** — Java SPI service descriptor

Source code: [`spi/src/main/java/com/mideye/keycloak/`](spi/src/main/java/com/mideye/keycloak/)

---

## File structure

```
keycloak/
├── README.md                  ← This file
├── docker-compose.yml         ← Keycloak + PostgreSQL
├── .env.example               ← Example environment variables
├── realm-export.json          ← Pre-configured realm (import into Keycloak)
├── providers/                 ← Keycloak provider JARs (mounted into container)
│   └── mideye-magic-link-authenticator.jar
├── spi/                       ← Custom Mideye Magic Link authenticator source
│   ├── pom.xml
│   └── src/main/java/com/mideye/keycloak/
│       ├── MideyeMagicLinkAuthenticator.java
│       └── MideyeMagicLinkAuthenticatorFactory.java
└── test-app/
    └── index.html             ← Minimal OIDC test page
```

## References

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak Docker Getting Started](https://www.keycloak.org/getting-started/getting-started-docker)
- [Mideye Magic Link API](https://www.mideye.com/docs/integrations/api/magic-link-api/)
