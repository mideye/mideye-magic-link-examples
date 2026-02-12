///usr/bin/env java --source 17 "$0" "$@"; exit $?
/**
 * Mideye Magic Link — Java Example
 * Passwordless authentication in a single file.
 * No external dependencies — uses only the Java standard library.
 *
 * Run:  java app.java
 * Open: http://localhost:8080
 */
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;

public class app {
    // ── Configuration ───────────────────────────────────────────
    static final String MIDEYE_URL = System.getenv().getOrDefault(
            "MIDEYE_URL", "https://mideye.domain.local:8443");
    static final String API_KEY = System.getenv().getOrDefault(
            "MIDEYE_API_KEY", "your-api-key-here");
    // ────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", app::handle);
        server.start();
        System.out.println("Mideye URL: " + MIDEYE_URL);
        System.out.println("Listening on http://localhost:8080");
    }

    static void handle(HttpExchange ex) throws IOException {
        String result = null;
        String phone = "";

        if ("POST".equals(ex.getRequestMethod())) {
            var body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            phone = URLDecoder.decode(
                    body.replace("phone=", ""), StandardCharsets.UTF_8);

            try {
                var encoded = URLEncoder.encode(phone, StandardCharsets.UTF_8);
                var req = HttpRequest.newBuilder()
                        .uri(URI.create(MIDEYE_URL + "/api/sfwa/auth?msisdn=" + encoded))
                        .header("api-key", API_KEY)
                        .timeout(java.time.Duration.ofSeconds(120))
                        .GET().build();
                var client = HttpClient.newBuilder().build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                // Simple JSON extraction — no library needed
                var json = resp.body();
                var start = json.indexOf("\"code\":\"") + 8;
                var end = json.indexOf("\"", start);
                result = json.substring(start, end);
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
        }
        var html = page(result, phone);
        var bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static String page(String result, String phone) {
        var resultHtml = "";
        if (result == null) {
            resultHtml = """
                <form method="post">
                  <label for="phone">Phone number</label>
                  <input type="tel" id="phone" name="phone" placeholder="+46701234567" required>
                  <button type="submit">Send authentication</button>
                </form>""";
        } else if ("TOUCH_ACCEPTED".equals(result)) {
            resultHtml = "<div class='result success'>✅ Authentication successful</div>";
        } else {
            resultHtml = "<div class='result failure'>❌ " + result + "</div>"
                    + "<form method='post' style='margin-top:1rem'>"
                    + "<input type='hidden' name='phone' value='" + phone + "'>"
                    + "<button type='submit'>Try again</button></form>";
        }
        return """
            <!DOCTYPE html>
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
            </div></body></html>""".formatted(resultHtml);
    }
}
