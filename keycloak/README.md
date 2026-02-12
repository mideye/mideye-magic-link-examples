# Keycloak + Mideye Magic Link Integration# Keycloak + Mideye Server Integration Test Environment



Add Mideye MFA to Keycloak using the **Magic Link API** — a custom Keycloak authenticator (Java SPI) that calls the Mideye Server HTTP API directly.This directory contains everything needed to run Keycloak in Docker and integrate it with Mideye Server for MFA authentication.



## How it works## Architecture



``````

User                 Keycloak                    Mideye Server┌─────────────────┐      OIDC        ┌─────────────────┐     MFA (HTTPS     ┌─────────────────┐

 │                      │                             ││                 │  ─────────────►  │                 │     or RADIUS)     │                 │

 │  username/password   │                             ││   Web App       │                  │   Keycloak      │  ─────────────►   │  Mideye Server  │

 │─────────────────────►│                             ││   (browser)     │  ◄─────────────  │   (Docker)      │  ◄─────────────   │  (your server)  │

 │                      │  GET /api/sfwa/auth?msisdn= ││                 │   ID Token       │   :8080         │   Accept/Reject   │                 │

 │                      │────────────────────────────►│└─────────────────┘                  └─────────────────┘                   └─────────────────┘

 │                      │                             │── push/SMS to user's phone                                            │

 │                      │                             │                                            ▼

 │         (user responds on phone)                   │                                     ┌─────────────────┐

 │                      │                             │                                     │  PostgreSQL     │

 │                      │  {"code":"TOUCH_ACCEPTED"}  │                                     │  (Docker)       │

 │                      │◄────────────────────────────│                                     │  :5432          │

 │   OIDC tokens        │                             │                                     └─────────────────┘

 │◄─────────────────────│                             │```

```

**How it works:**

1. User enters username + password in Keycloak

2. After password verification, the custom authenticator calls the Magic Link API1. User opens a web application and clicks "Sign in"

3. Mideye Server sends a push notification (Mideye+) or SMS OTP to the user's phone2. Keycloak shows the login page (username + password)

4. The API blocks until the user responds3. After password verification, Keycloak triggers MFA via Mideye Server

5. If `{"code":"TOUCH_ACCEPTED"}` → Keycloak completes login and issues OIDC tokens4. Mideye Server sends a push notification (Mideye+) or SMS OTP to the user's phone

5. User responds → Mideye accepts/rejects → Keycloak completes login → user gets OIDC tokens

## Prerequisites

## Prerequisites

- **Docker** and **Docker Compose** installed

- **Mideye Server** running and accessible from the Docker host- **Docker** and **Docker Compose** installed

- A user provisioned in Mideye Server with a phone number- **Mideye Server** running and accessible from the Docker host

- A **Magic Link endpoint** configured in Mideye Server with an API key (see [Magic Link API docs](https://www.mideye.com/docs/integrations/api/magic-link-api/))- A user provisioned in Mideye Server with a phone number



------



## 1. Configure Mideye Server connection## 1. Start Keycloak



Copy the example environment file and fill in your values:Edit `docker-compose.yml` and set your Mideye Server connection:



```bash```yaml

cp .env.example .envMIDEYE_URL: https://your-mideye-server:8443

```MIDEYE_API_KEY: your-api-key-here

```

Edit `.env`:

Then start:

```bash

MIDEYE_URL=https://your-mideye-server:8443```bash

MIDEYE_API_KEY=your-api-key-heredocker compose up -d

``````



Or edit `docker-compose.yml` directly — update the `MIDEYE_URL` and `MIDEYE_API_KEY` environment variables under the `keycloak` service.This starts:

- **Keycloak** on http://localhost:8080 (admin/admin)

---- **PostgreSQL** as the backing database



## 2. Start KeycloakWait ~30 seconds, then verify Keycloak is running:



```bash```bash

docker compose up -ddocker compose logs -f keycloak

``````



This starts:Look for: `Keycloak 26.0.7 on JVM (powered by Quarkus) started`

- **Keycloak** on http://localhost:8080 (admin/admin)

- **PostgreSQL** as the backing database---



Wait ~30 seconds, then verify Keycloak is running:## 2. Log in to Keycloak Admin Console



```bashOpen http://localhost:8080/admin and log in:

docker compose logs -f keycloak- **Username:** `admin`

```- **Password:** `admin`



Look for: `Keycloak 26.0.7 on JVM (powered by Quarkus) started`---



---## 3. Create the Realm



## 3. Create the realmYou have two options:



Open http://localhost:8080/admin and log in with `admin` / `admin`.### Option A: Import from file (recommended)



### Option A: Import from file (recommended)1. In the top-left corner, click **Keycloak** → **Create Realm**

2. Click **Browse...** and upload `realm-export.json` from this directory

1. In the top-left corner, click **Keycloak** → **Create Realm**3. Click **Create**

2. Click **Browse...** and upload `realm-export.json` from this directory

3. Click **Create**This creates:

- Realm: `mideye-test`

This creates:- User Profile: `phoneNumber` attribute registered (so it appears on the user Attributes tab)

- Realm: `mideye-test`- User: `testuser` / `testpassword` (phone: `+46701234567`)

- User Profile: `phoneNumber` attribute registered (so it appears on the user Attributes tab)- Client: `test-app` (OIDC, redirect to `http://localhost:3000/*`)

- User: `testuser` / `testpassword` (phone: `+46701234567`)

- Client: `test-app` (OIDC, redirect to `http://localhost:3000/*`)> **After import:** Go to **Users** → `testuser` → **Attributes** and change `phoneNumber` to a real phone number registered in your Mideye Server.



> **After import:** Go to **Users** → `testuser` → **Attributes** and change `phoneNumber` to a real phone number registered in your Mideye Server.### Option B: Create manually



### Option B: Create manually1. Click **Create Realm** → name it `mideye-test` → **Create**

2. Register the `phoneNumber` attribute: see [Register the phoneNumber attribute](#register-the-phonenumber-attribute)

1. Click **Create Realm** → name it `mideye-test` → **Create**3. Create user: see [Create a Test User](#create-a-test-user)

2. Register the `phoneNumber` attribute (see [Register the phoneNumber attribute](#register-the-phonenumber-attribute) below)4. Create client: see [Create a Test Client](#create-a-test-client)

3. Create a test user (see [Create a test user](#create-a-test-user) below)

---

---

## 4. Choose Integration Path

## 4. Deploy the authenticator JAR

There are two ways to connect Keycloak to Mideye Server for MFA:

The pre-built JAR is in `providers/`. It's already mounted into the Keycloak container via `docker-compose.yml`.

| Path | Protocol | Guide |

Restart Keycloak to load the provider:|------|----------|-------|

| **Magic Link API** (recommended) | HTTPS (port 8443) | [MAGIC-LINK.md](MAGIC-LINK.md) |

```bash| **RADIUS** | UDP (port 1812) | [RADIUS.md](RADIUS.md) |

docker compose restart keycloak

```### Which one should I use?



Verify the provider loaded — check the logs for any errors:- **Magic Link API** — Simpler setup. Uses a custom Keycloak authenticator (Java SPI) that calls the Mideye HTTP API directly. Supports push (Mideye+) and SMS. No RADIUS configuration needed.

- **RADIUS** — Traditional approach. Requires a third-party Keycloak RADIUS plugin and RADIUS client configuration in Mideye Server. Supports all Mideye auth types (OTP, Touch, Challenge-Response).

```bash

docker compose logs keycloak | grep -i "mideye\|provider\|error"---

```

## Register the phoneNumber attribute

> **Building from source (optional):** If you need to rebuild the JAR, you need Java 17+ and Maven:

> ```bash> Skip this if you imported `realm-export.json` — the attribute is already registered.

> cd spi

> mvn clean packageKeycloak 26 requires custom attributes to be registered in the **User Profile** schema before they appear on the user's Attributes tab.

> cp target/mideye-magic-link-authenticator.jar ../providers/

> docker compose restart keycloak1. In the left sidebar, go to **Realm settings**

> ```2. Click the **User profile** tab

3. Click the **JSON editor** sub-tab

---4. In the `"attributes"` array, add the following entry after the `lastName` attribute:



## 5. Create the authentication flow```json

{

1. In Keycloak Admin Console → make sure you're in the **mideye-test** realm  "name": "phoneNumber",

2. Go to **Authentication** (left sidebar)  "displayName": "Phone number",

3. Click **Create flow** (top right)  "validations": {

4. Fill in:    "length": { "max": 255 }

   - **Name:** `Mideye MFA Browser`  },

   - **Flow type:** `Basic flow`  "permissions": {

   - **Description:** `Username/password + Mideye Magic Link MFA`    "view": ["admin", "user"],

5. Click **Create**    "edit": ["admin", "user"]

  },

You'll see an empty flow:  "multivalued": false

}

> *No steps — You can start defining this flow by adding a sub-flow or an execution*```



### Add the username/password step5. Click **Save**



1. Click **Add execution**The `phoneNumber` field will now appear when editing a user's Attributes.

2. In the search field, search for **Username Password Form**

3. Select it and click **Add**---

4. The step appears in the flow — set its requirement to **Required**

## Create a Test User

### Add the Mideye MFA step

> Skip this if you imported `realm-export.json` — the user is already created.

1. Click **Add execution** again>

2. Search for **Mideye Magic Link**> Make sure you have [registered the phoneNumber attribute](#register-the-phonenumber-attribute) first.

3. Select it and click **Add**

4. Set its requirement to **Required**1. In Keycloak Admin Console → **mideye-test** realm

2. Go to **Users** → **Create new user**

> **"Mideye Magic Link" not in the list?** The JAR wasn't loaded. Check `docker compose logs keycloak` for errors and make sure you restarted Keycloak after copying the JAR to `providers/`.3. Fill in:

   - **Username:** `testuser`

The flow should now look like this:   - **Email:** your email

   - **First name / Last name:** your name

| Step | Type | Requirement |4. Click **Create**

|------|------|-------------|5. Go to the **Credentials** tab → **Set password**

| Username Password Form | execution | **Required** |   - Enter a password

| Mideye Magic Link | execution | **Required** |   - Toggle **Temporary** to **Off**

6. Go to the **Attributes** tab

### Bind the flow to the browser   - Set **Phone number** to the user's phone number in E.164 format (e.g. `+46701234567`)

7. Click **Save**

1. Go back to **Authentication** (left sidebar) to see the list of all flows

2. Find the **Mideye MFA Browser** flow in the list> **Important:** The phone number must match a user provisioned in Mideye Server.

3. Click the **⋮** (three dots) menu on that row → **Bind flow**

4. Select **Browser flow**## Create a Test Client

5. Click **Save**

> Skip this if you imported `realm-export.json` — the client is already created.

---

1. Go to **Clients** → **Create client**

## 6. Verify the user has a phone number2. Settings:

   - **Client type:** `OpenID Connect`

The authenticator looks up the user's phone number from the `phoneNumber` attribute.   - **Client ID:** `test-app`

3. Click **Next**

1. Go to **Users** → click on your test user4. Confirm **Standard flow** is enabled

2. Go to the **Attributes** tab5. Click **Next**

3. Verify there's a **Phone number** field with a valid phone number (e.g. `+46701234567`)6. Set:

4. This phone number must match a user provisioned in Mideye Server   - **Valid redirect URIs:** `http://localhost:3000/*`

   - **Web origins:** `http://localhost:3000`

> If you imported `realm-export.json`, the test user already has `+46701234567`. Change this to a real phone number.7. Click **Save**



> **Don't see the Phone number field?** You need to register the `phoneNumber` attribute in the User Profile first. See [Register the phoneNumber attribute](#register-the-phonenumber-attribute).---



---## Test the Login Flow



## 7. Test the login### Using the Keycloak Account Console



Open http://localhost:8080/realms/mideye-test/accountOpen: http://localhost:8080/realms/mideye-test/account



1. Click **Sign in**1. Click **Sign in**

2. Enter the username and password2. Enter `testuser` and the password

3. Wait — Mideye MFA is triggered:3. Mideye MFA is triggered → respond on your phone

   - If the user has Mideye+ → a push notification appears on their phone4. You should be logged in to the Account Console

   - If SMS → an OTP is sent

4. Respond on the phone### Using the test HTML page

5. You should be redirected back and logged in

A minimal OIDC test page is included at `test-app/index.html`:

### Check Keycloak logs during authentication

```bash

```bashcd test-app

docker compose logs -f keycloakpython3 -m http.server 3000

``````



You should see:Open http://localhost:3000 and click **Sign in with Keycloak**.

```

Mideye MFA: Initiating authentication for user 'testuser' (phone: ***4567)---

Mideye MFA: Authentication ACCEPTED for user 'testuser'

```## Stop / Clean Up



---```bash

# Stop containers (keep data)

## 8. Test with the OIDC test page (optional)docker compose down



A minimal OIDC test page is included at `test-app/index.html` — it shows the full Authorization Code + PKCE flow and displays the decoded ID token claims.# Stop and remove ALL data (database, volumes)

docker compose down -v

### Create the OIDC client in Keycloak```



> Skip this if you imported `realm-export.json` — the `test-app` client is already created.---



1. In Keycloak Admin Console → **mideye-test** realm## Troubleshooting

2. Go to **Clients** (left sidebar) → **Create client**

3. **General settings:**### Keycloak won't start

   - **Client type:** `OpenID Connect`

   - **Client ID:** `test-app````bash

4. Click **Next**docker compose logs keycloak

5. **Capability config:**```

   - Confirm **Standard flow** is enabled

   - **Client authentication:** leave **Off** (this is a public client)Common issues:

6. Click **Next**- **Port 8080 already in use** → change the port mapping in `docker-compose.yml`

7. **Login settings:**- **Database connection failed** → wait a few seconds (PostgreSQL may still be starting)

   - **Root URL:** `http://localhost:3000`- **Provider JAR errors** → check the JAR was built for the correct Keycloak version

   - **Valid redirect URIs:** `http://localhost:3000/*`

   - **Web origins:** `http://localhost:3000`### Docker networking

8. Click **Save**

If Mideye Server runs on the Docker host machine:

### Start the test page- **macOS / Windows:** Use `host.docker.internal` as the server address

- **Linux:** Add `extra_hosts: ["host.docker.internal:host-gateway"]` to `docker-compose.yml` or use the host's actual IP address

```bash

cd test-app---

python3 -m http.server 3000

```## File Structure



### Sign in```

keycloak/

1. Open http://localhost:3000├── README.md                  ← This file (start here)

2. Verify the settings match your setup:├── MAGIC-LINK.md              ← Magic Link API integration guide

   - **Keycloak URL:** `http://localhost:8080`├── RADIUS.md                  ← RADIUS integration guide

   - **Realm:** `mideye-test`├── docker-compose.yml         ← Keycloak + PostgreSQL

   - **Client ID:** `test-app`├── realm-export.json          ← Pre-configured realm (import into Keycloak)

3. Click **Sign in with Keycloak**├── providers/                 ← Keycloak provider JARs (mounted into container)

4. You are redirected to Keycloak → enter username and password├── spi/                       ← Custom Mideye Magic Link authenticator SPI

5. Mideye MFA is triggered → respond on your phone│   ├── pom.xml

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
