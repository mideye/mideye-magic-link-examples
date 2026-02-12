"""
Mideye Magic Link — Python Example
Passwordless authentication in a single file.

Run:  python app.py
Open: http://localhost:8080
"""
import os
import requests
import urllib.parse
from flask import Flask, render_template_string, request

# ── Configuration ───────────────────────────────────────────────
MIDEYE_URL = os.getenv("MIDEYE_URL", "https://mideye.domain.local:8443")
API_KEY    = os.getenv("MIDEYE_API_KEY", "your-api-key-here")
# ────────────────────────────────────────────────────────────────

app = Flask(__name__)

PAGE = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Mideye Login</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: system-ui, -apple-system, sans-serif;
           display: flex; align-items: center; justify-content: center;
           min-height: 100vh; background: #f5f7fa; color: #1a1a2e; }
    .card { background: #fff; border-radius: 12px; padding: 2.5rem;
            box-shadow: 0 4px 24px rgba(0,0,0,.08); max-width: 400px; width: 100%; }
    h1 { font-size: 1.4rem; margin-bottom: 0.5rem; }
    p.sub { color: #666; font-size: 0.9rem; margin-bottom: 1.5rem; }
    label { font-size: 0.85rem; font-weight: 600; display: block; margin-bottom: 0.3rem; }
    input { width: 100%; padding: 0.7rem 0.9rem; border: 1px solid #ddd;
            border-radius: 8px; font-size: 1rem; margin-bottom: 1rem; }
    input:focus { outline: none; border-color: #4361ee; box-shadow: 0 0 0 3px rgba(67,97,238,.15); }
    button { width: 100%; padding: 0.8rem; background: #4361ee; color: #fff;
             border: none; border-radius: 8px; font-size: 1rem; font-weight: 600;
             cursor: pointer; transition: background 0.2s; }
    button:hover { background: #3651d4; }
    .result { margin-top: 1.5rem; padding: 1rem; border-radius: 8px; font-size: 0.9rem; }
    .result.success { background: #ecfdf5; color: #065f46; border: 1px solid #a7f3d0; }
    .result.failure { background: #fef2f2; color: #991b1b; border: 1px solid #fca5a5; }
  </style>
</head>
<body>
  <div class="card">
    <h1>🔐 Mideye Login</h1>
    <p class="sub">Enter your phone number to authenticate.</p>
    {% if not result %}
    <form method="post">
      <label for="phone">Phone number</label>
      <input type="tel" id="phone" name="phone" placeholder="+46701234567" required>
      <button type="submit">Send authentication</button>
    </form>
    {% elif result == 'TOUCH_ACCEPTED' %}
    <div class="result success">✅ Authentication successful</div>
    {% else %}
    <div class="result failure">❌ {{ result }}</div>
    <form method="post" style="margin-top: 1rem;">
      <input type="hidden" name="phone" value="{{ phone }}">
      <button type="submit">Try again</button>
    </form>
    {% endif %}
  </div>
</body>
</html>
"""


@app.route("/", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        phone = request.form["phone"]
        try:
            resp = requests.get(
                f"{MIDEYE_URL}/api/sfwa/auth",
                params={"msisdn": phone},
                headers={"api-key": API_KEY},
                timeout=120,
                verify=True,
            )
            data = resp.json()
            code = data.get("code", "UNKNOWN_ERROR")
        except requests.Timeout:
            code = "TIMEOUT"
        except Exception as e:
            code = f"ERROR: {e}"
        return render_template_string(PAGE, result=code, phone=phone)
    return render_template_string(PAGE, result=None, phone=None)


if __name__ == "__main__":
    print(f"Mideye URL: {MIDEYE_URL}")
    print(f"Listening on http://localhost:8080")
    app.run(host="0.0.0.0", port=8080, debug=True)
