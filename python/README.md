# Mideye Magic Link — Python Example

A single-file web application demonstrating passwordless authentication with the Mideye Magic Link API.

## Prerequisites

### macOS

```bash
# Python 3.8+ is pre-installed on macOS or install via Homebrew
brew install python3

# Install dependencies
pip3 install -r requirements.txt
```

### Linux (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install python3 python3-pip python3-venv

# Create a virtual environment (recommended)
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### Linux (RHEL/Rocky/Alma)

```bash
sudo dnf install python3 python3-pip

# Create a virtual environment (recommended)
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### Windows

```powershell
# Install Python from https://www.python.org/downloads/
# Make sure to check "Add Python to PATH" during installation

# Install dependencies
pip install -r requirements.txt
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
python app.py
```

Open **http://localhost:8080** in your browser.

## How It Works

1. The app shows a login form with a phone number input
2. When submitted, it calls `GET /api/sfwa/auth?msisdn={phone}` with the API key header
3. The call blocks until the user responds on their phone (push or SMS)
4. The result is displayed — green for accepted, red for rejected/timeout
