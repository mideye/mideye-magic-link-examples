# Mideye Magic Link — Go Example

A single-file web application demonstrating passwordless authentication with the Mideye Magic Link API. No external dependencies — uses only the Go standard library.

## Prerequisites

### macOS

```bash
# Install Go via Homebrew
brew install go

# Verify installation
go version
```

### Linux (Debian/Ubuntu)

```bash
# Option 1: Via package manager (may not be the latest version)
sudo apt update
sudo apt install golang-go

# Option 2: Download latest from go.dev (recommended)
wget https://go.dev/dl/go1.22.0.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go1.22.0.linux-amd64.tar.gz
export PATH=$PATH:/usr/local/go/bin

# Verify installation
go version
```

### Linux (RHEL/Rocky/Alma)

```bash
sudo dnf install golang

# Verify installation
go version
```

### Windows

Download and install from [go.dev/dl](https://go.dev/dl/).

Verify in PowerShell:

```powershell
go version
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
go run main.go
```

Open **http://localhost:8080** in your browser.

## How It Works

1. The app starts an HTTP server on port 8080 using `net/http`
2. When a phone number is submitted, it calls `GET /api/sfwa/auth?msisdn={phone}` with the API key header
3. Uses only standard library packages (`net/http`, `encoding/json`) — no external dependencies
4. The call blocks until the user responds on their phone (push or SMS)
5. The result is displayed — green for accepted, red for rejected/timeout
