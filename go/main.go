// Mideye Magic Link — Go Example
// Passwordless authentication in a single file, zero external dependencies.
//
// Configuration (environment variables):
//   MIDEYE_URL     — Base URL of your MideyeServer (default: https://mideye.domain.local:8443)
//   MIDEYE_API_KEY — Your API key (default: your-api-key-here)
//
// Run:  go run main.go
// Open: http://localhost:8080

package main

import (
	"encoding/json"
	"fmt"
	"html"
	"io"
	"net/http"
	"net/url"
	"os"
	"time"
)

// ── Configuration ───────────────────────────────────────────────

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

var (
	mideyeURL = env("MIDEYE_URL", "https://mideye.domain.local:8443")
	apiKey    = env("MIDEYE_API_KEY", "your-api-key-here")
)

// ────────────────────────────────────────────────────────────────

type authResponse struct {
	Code string `json:"code"`
}

func main() {
	http.HandleFunc("/", handler)
	fmt.Println("─────────────────────────────────────────")
	fmt.Println("  Mideye Magic Link — Go Example")
	fmt.Println("  http://localhost:8080")
	fmt.Printf("  API endpoint: %s/api/sfwa/auth\n", mideyeURL)
	fmt.Println("─────────────────────────────────────────")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		fmt.Fprintf(os.Stderr, "Server failed: %v\n", err)
		os.Exit(1)
	}
}

func handler(w http.ResponseWriter, r *http.Request) {
	var result, phone string

	if r.Method == "POST" {
		r.ParseForm()
		phone = r.FormValue("phone")
		encoded := url.QueryEscape(phone)

		client := &http.Client{Timeout: 120 * time.Second}
		req, _ := http.NewRequest("GET",
			fmt.Sprintf("%s/api/sfwa/auth?msisdn=%s", mideyeURL, encoded), nil)
		req.Header.Set("api-key", apiKey)

		resp, err := client.Do(req)
		if err != nil {
			result = fmt.Sprintf("ERROR: %v", err)
		} else {
			defer resp.Body.Close()
			body, _ := io.ReadAll(resp.Body)
			var ar authResponse
			if json.Unmarshal(body, &ar) == nil {
				result = ar.Code
			} else {
				result = "PARSE_ERROR"
			}
		}
	}

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, page(result, phone))
}

func page(result, phone string) string {
	var body string
	switch {
	case result == "":
		body = `<form method="post">
			<label for="phone">Phone number</label>
			<input type="tel" id="phone" name="phone" placeholder="+46701234567" required>
			<button type="submit">Send authentication</button>
		</form>`
	case result == "TOUCH_ACCEPTED":
		body = `<div class="result success">✅ Authentication successful</div>`
	default:
		body = fmt.Sprintf(`<div class="result failure">❌ %s</div>
			<form method="post" style="margin-top:1rem">
			<input type="hidden" name="phone" value="%s">
			<button type="submit">Try again</button></form>`,
			html.EscapeString(result), html.EscapeString(phone))
	}

	return fmt.Sprintf(`<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Mideye Login</title>
<style>
  *{margin:0;padding:0;box-sizing:border-box}
  body{font-family:system-ui,-apple-system,sans-serif;display:flex;
       align-items:center;justify-content:center;min-height:100vh;
       background:#f5f7fa;color:#1a1a2e}
  .card{background:#fff;border-radius:12px;padding:2.5rem;
        box-shadow:0 4px 24px rgba(0,0,0,.08);max-width:400px;width:100%%}
  h1{font-size:1.4rem;margin-bottom:.5rem}
  p.sub{color:#666;font-size:.9rem;margin-bottom:1.5rem}
  label{font-size:.85rem;font-weight:600;display:block;margin-bottom:.3rem}
  input{width:100%%;padding:.7rem .9rem;border:1px solid #ddd;
        border-radius:8px;font-size:1rem;margin-bottom:1rem}
  input:focus{outline:none;border-color:#4361ee;box-shadow:0 0 0 3px rgba(67,97,238,.15)}
  button{width:100%%;padding:.8rem;background:#4361ee;color:#fff;border:none;
         border-radius:8px;font-size:1rem;font-weight:600;cursor:pointer}
  button:hover{background:#3651d4}
  .result{margin-top:1.5rem;padding:1rem;border-radius:8px;font-size:.9rem}
  .result.success{background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0}
  .result.failure{background:#fef2f2;color:#991b1b;border:1px solid #fca5a5}
</style></head><body><div class="card">
<h1>🔐 Mideye Login</h1>
<p class="sub">Enter your phone number to authenticate.</p>
%s
</div></body></html>`, body)
}
