# Mideye Magic Link — C# / .NET Example

A single-file web application demonstrating passwordless authentication with the Mideye Magic Link API using ASP.NET Core Minimal API.

## Prerequisites

### macOS

```bash
# Install .NET 8 via Homebrew
brew install dotnet@8

# Verify installation
dotnet --version
```

### Linux (Debian/Ubuntu)

```bash
# Add Microsoft package repository
wget https://packages.microsoft.com/config/ubuntu/$(lsb_release -rs)/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
sudo dpkg -i packages-microsoft-prod.deb
rm packages-microsoft-prod.deb

# Install .NET 8 SDK
sudo apt update
sudo apt install dotnet-sdk-8.0

# Verify installation
dotnet --version
```

### Linux (RHEL/Rocky/Alma)

```bash
sudo dnf install dotnet-sdk-8.0

# Verify installation
dotnet --version
```

### Windows

Download and install from [dot.net/download](https://dotnet.microsoft.com/download/dotnet/8.0).

Verify in PowerShell:

```powershell
dotnet --version
```

## Setup

The project is already initialized. If you want to recreate it from scratch:

```bash
dotnet new web -o MideyeLogin
# Then replace Program.cs with the one in this directory
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
dotnet run
```

Open **http://localhost:8080** in your browser.

## How It Works

1. The app starts an ASP.NET Core Minimal API server on port 8080
2. When a phone number is submitted, it calls `GET /api/sfwa/auth?msisdn={phone}` with the API key header
3. Uses `HttpClient` from the .NET standard library for the API call
4. The call blocks until the user responds on their phone (push or SMS)
5. The result is displayed — green for accepted, red for rejected/timeout
