# Mideye Magic Link — Java Example

A single-file web application demonstrating passwordless authentication with the Mideye Magic Link API. No external dependencies — uses only the Java standard library.

## Prerequisites

### macOS

```bash
# Install Java 17+ via Homebrew
brew install openjdk@17

# Verify installation
java --version
```

### Linux (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install openjdk-17-jdk

# Verify installation
java --version
```

### Linux (RHEL/Rocky/Alma)

```bash
sudo dnf install java-17-openjdk java-17-openjdk-devel

# Verify installation
java --version
```

### Windows

Download and install from [Adoptium (Eclipse Temurin)](https://adoptium.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/).

Make sure Java is on your `PATH`:

```powershell
java --version
```

## Configuration

Set the environment variables for your Mideye Server:

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

## Run

```bash
java app.java
```

Open **http://localhost:8080** in your browser.

> **Note:** The `///usr/bin/env` shebang on line 1 allows running the file directly on macOS/Linux:
> `./app.java` (after `chmod +x app.java`). On Windows, use `java app.java`.

## How It Works

1. The app starts an HTTP server on port 8080 using `com.sun.net.httpserver`
2. When a phone number is submitted, it calls `GET /api/sfwa/auth?msisdn={phone}` with the API key header
3. Uses `java.net.http.HttpClient` (no external libraries) for the API call
4. The call blocks until the user responds on their phone (push or SMS)
5. The result is displayed — green for accepted, red for rejected/timeout
