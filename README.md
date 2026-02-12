# Mideye Magic Link — Example Applications

Ready-to-run example applications that demonstrate passwordless authentication using the [Mideye Magic Link API](https://www.mideye.com/docs/integrations/api/magic-link-api/).

Each example is a **complete, single-file web application** — enter a phone number, Mideye sends a push notification or SMS magic link, and the user taps **Accept** or **Reject**.

## Available Examples

| Language | Directory | Dependencies | Runtime |
|----------|-----------|-------------|---------|
| [Python](python/) | `python/` | Flask, Requests | Python 3.8+ |
| [Java](java/) | `java/` | None (stdlib only) | Java 17+ |
| [C# / .NET](dotnet/) | `dotnet/` | ASP.NET Core Minimal API | .NET 8+ |
| [Go](go/) | `go/` | None (stdlib only) | Go 1.21+ |

Don't want to run a full app? Jump to [Quick API Test](#quick-api-test-no-app-required) for one-liner examples using **cURL**, **HTTPie**, **PowerShell**, **wget**, and **Postman**.

## Quick Start

### 1. Prerequisites

You need a running **Mideye Server** with a **Magic Link endpoint** configured:

1. In the Mideye web GUI, go to **External Endpoints** → **Magic Link Endpoints**
2. Create a new endpoint (or use an existing one)
3. Generate an API key: **Edit** → **API Token Management** → **Create New API Token**

### 2. Configuration

Each example uses two environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `MIDEYE_URL` | Your Mideye Server base URL | `https://mideye.domain.local:8443` |
| `MIDEYE_API_KEY` | API key from the Magic Link endpoint | `c3859cad-479a-4d65-9253-459ea4a12b34` |

Set them before running any example:

**macOS / Linux:**

```bash
export MIDEYE_URL="https://mideye.domain.local:8443"
export MIDEYE_API_KEY="your-api-key-here"
```

**Windows (PowerShell):**

```powershell
$env:MIDEYE_URL = "https://mideye.domain.local:8443"
$env:MIDEYE_API_KEY = "your-api-key-here"
```

**Windows (Command Prompt):**

```cmd
set MIDEYE_URL=https://mideye.domain.local:8443
set MIDEYE_API_KEY=your-api-key-here
```

### 3. Run

Pick your language and follow the instructions in the corresponding directory, or jump straight in:

```bash
# Python
cd python && pip install -r requirements.txt && python app.py

# Java
cd java && java app.java

# C# / .NET
cd dotnet && dotnet run

# Go
cd go && go run main.go
```

Then open **http://localhost:8080** in your browser.

## Quick API Test (No App Required)

You can test the Magic Link API directly from the command line or an API client before running any example app. Replace the URL and API key with your own values.

### cURL

```bash
curl -s -H "api-key: YOUR_API_KEY" \
  "https://mideye.domain.local:8443/api/sfwa/auth?msisdn=%2B46701234567"
```

Example responses:

```json
{"code":"TOUCH_ACCEPTED"}      # User accepted
{"code":"TOUCH_REJECTED"}      # User rejected
{"code":"USER_NOT_RESPONDED"}  # Timeout
```

> The `%2B` is the URL-encoded `+` sign. You can also use `--data-urlencode` to let cURL handle encoding:
>
> ```bash
> curl -s -G -H "api-key: YOUR_API_KEY" \
>   --data-urlencode "msisdn=+46701234567" \
>   "https://mideye.domain.local:8443/api/sfwa/auth"
> ```

If your server uses a self-signed certificate, add `-k` to skip TLS verification (testing only).

### HTTPie

```bash
http GET "https://mideye.domain.local:8443/api/sfwa/auth" \
  msisdn=="+46701234567" \
  api-key:YOUR_API_KEY
```

### PowerShell

```powershell
$headers = @{ "api-key" = "YOUR_API_KEY" }
Invoke-RestMethod -Uri "https://mideye.domain.local:8443/api/sfwa/auth?msisdn=%2B46701234567" -Headers $headers
```

### wget

```bash
wget -qO- --header="api-key: YOUR_API_KEY" \
  "https://mideye.domain.local:8443/api/sfwa/auth?msisdn=%2B46701234567"
```

### Postman

1. Create a new **GET** request
2. Set the URL to:
   ```
   https://mideye.domain.local:8443/api/sfwa/auth
   ```
3. Add a **query parameter**:
   | Key | Value |
   |-----|-------|
   | `msisdn` | `+46701234567` |
4. Add a **header**:
   | Key | Value |
   |-----|-------|
   | `api-key` | `YOUR_API_KEY` |
5. Click **Send** — the request will block until the user responds on their phone
6. Expected responses:

   **User accepts:**
   ```json
   {"code":"TOUCH_ACCEPTED"}
   ```
   **User rejects:**
   ```json
   {"code":"TOUCH_REJECTED"}
   ```
   **User does not respond (timeout):**
   ```json
   {"code":"USER_NOT_RESPONDED"}
   ```

> **Tip:** Set the Postman timeout to at least 120 seconds (**Settings → General → Request timeout**), since the API blocks until the user responds.

### Insomnia / Bruno / Hoppscotch

The setup is identical to Postman — create a GET request with the same URL, query parameter, and `api-key` header. All modern API clients work the same way.

## How It Works

```
┌──────────────┐     GET /api/sfwa/auth       ┌──────────────────┐
│  Your App    │ ──────────────────────────►  │  Mideye Server   │
│  (this code) │     + api-key header         │                  │
│              │                              │                  │
│              │  ◄─── blocks until ─────────►│  → Mideye Switch │
│              │       user responds          │    → Push / SMS  │
│              │                              │    → User's Phone│
│              │  {"code":"TOUCH_ACCEPTED"}   │                  │
│  ◄───────────│ ◄──────────────────────────  │                  │
│  Show result │                              └──────────────────┘
└──────────────┘
```

1. Your app sends a **GET** request with the phone number and API key
2. Mideye Server creates a magic link and sends the authentication request to Mideye Switch
3. Switch delivers via **Mideye+ push** (if installed) or **SMS magic link**
4. The API call **blocks** until the user responds or the timeout expires
5. Your app receives the result: `TOUCH_ACCEPTED`, `TOUCH_REJECTED`, `USER_NOT_RESPONDED`, etc.

## API Reference

**Endpoint:**

```
GET /api/sfwa/auth?msisdn={phone_number}
```

**Headers:**

| Header | Value |
|--------|-------|
| `api-key` | Your API key |

**Response:**

```json
{"code":"TOUCH_ACCEPTED"}
```

**Response codes — standard:**

| Code | Meaning |
|------|---------|
| `TOUCH_ACCEPTED` | User accepted the authentication |
| `TOUCH_REJECTED` | User rejected the authentication |
| `USER_NOT_RESPONDED` | Timeout — user did not respond within the allowed time |

**Response codes — errors:**

| Code | Meaning |
|------|---------|
| `FAILED_DELIVERY` | SMS or push could not be delivered |
| `BAD_REQUEST` | Invalid request (wrong API key, rate limited) |

For full API documentation, see [Magic Link API Reference](https://www.mideye.com/docs/integrations/api/magic-link-api/).

## TLS Certificate Verification

If your Mideye Server uses a self-signed certificate, the examples will fail with a TLS error. For **testing only**, you can disable certificate verification:

- **Python:** Change `verify=True` to `verify=False` in `app.py`
- **Java:** Not recommended — add the certificate to your Java truststore instead
- **C#:** Add `HttpClientHandler` with `ServerCertificateCustomValidationCallback`
- **Go:** Set `InsecureSkipVerify: true` in `tls.Config`

> ⚠️ **Never disable TLS verification in production.** Import the server's CA certificate into your trust store instead.

## Production Considerations

These examples are intentionally minimal. For production:

- **Secret management** — Store API keys in a vault or secret manager, not in environment variables or source code
- **CSRF protection** — Add anti-CSRF tokens to the login form
- **Async API calls** — Run the blocking API call in a background thread/task to avoid tying up the web server
- **HTTPS** — Serve your application over HTTPS
- **Input validation** — Validate and sanitize the phone number before sending it to the API
- **Rate limiting** — Add application-level rate limiting in addition to Mideye's built-in protection
- **Error handling** — Add proper logging and user-friendly error messages

## License

MIT License — see [LICENSE](LICENSE) for details.

## Support

- 📖 [Mideye Documentation](https://www.mideye.com/docs/)
- 📧 [support@mideye.com](mailto:support@mideye.com)
