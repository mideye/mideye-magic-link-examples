// Mideye Magic Link — C# Example
// Passwordless authentication in a single file.
//
// Run:  dotnet run
// Open: http://localhost:8080

using System.Web;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls("http://0.0.0.0:8080");
var app = builder.Build();

// ── Configuration ───────────────────────────────────────────────
var mideyeUrl = Environment.GetEnvironmentVariable("MIDEYE_URL")
    ?? "https://mideye.domain.local:8443";
var apiKey = Environment.GetEnvironmentVariable("MIDEYE_API_KEY")
    ?? "your-api-key-here";
// ────────────────────────────────────────────────────────────────

Console.WriteLine($"Mideye URL: {mideyeUrl}");
Console.WriteLine("Listening on http://localhost:8080");

var http = new HttpClient { Timeout = TimeSpan.FromSeconds(120) };

app.MapGet("/", () => Results.Content(Page(null), "text/html"));

app.MapPost("/", async (HttpRequest req) =>
{
    var form = await req.ReadFormAsync();
    var phone = form["phone"].ToString();
    string result;

    try
    {
        var encoded = HttpUtility.UrlEncode(phone);
        var request = new HttpRequestMessage(HttpMethod.Get,
            $"{mideyeUrl}/api/sfwa/auth?msisdn={encoded}");
        request.Headers.Add("api-key", apiKey);

        var response = await http.SendAsync(request);
        var json = await response.Content.ReadAsStringAsync();

        // Simple extraction — no JSON library needed for this
        var start = json.IndexOf("\"code\":\"") + 8;
        var end = json.IndexOf("\"", start);
        result = json[start..end];
    }
    catch (Exception ex)
    {
        result = $"ERROR: {ex.Message}";
    }

    return Results.Content(Page(result), "text/html");
});

app.Run();

static string Page(string? result)
{
    var body = result switch
    {
        null => """
            <form method="post">
              <label for="phone">Phone number</label>
              <input type="tel" id="phone" name="phone" placeholder="+46701234567" required>
              <button type="submit">Send authentication</button>
            </form>
            """,
        "TOUCH_ACCEPTED" =>
            "<div class='result success'>✅ Authentication successful</div>",
        _ => $"""
            <div class="result failure">❌ {result}</div>
            <form method="post" style="margin-top:1rem">
              <button type="submit">Try again</button>
            </form>
            """
    };

    return $$"""
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
                box-shadow:0 4px 24px rgba(0,0,0,.08);max-width:400px;width:100%}
          h1{font-size:1.4rem;margin-bottom:.5rem}
          p.sub{color:#666;font-size:.9rem;margin-bottom:1.5rem}
          label{font-size:.85rem;font-weight:600;display:block;margin-bottom:.3rem}
          input{width:100%;padding:.7rem .9rem;border:1px solid #ddd;
                border-radius:8px;font-size:1rem;margin-bottom:1rem}
          input:focus{outline:none;border-color:#4361ee;box-shadow:0 0 0 3px rgba(67,97,238,.15)}
          button{width:100%;padding:.8rem;background:#4361ee;color:#fff;border:none;
                 border-radius:8px;font-size:1rem;font-weight:600;cursor:pointer}
          button:hover{background:#3651d4}
          .result{margin-top:1.5rem;padding:1rem;border-radius:8px;font-size:.9rem}
          .result.success{background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0}
          .result.failure{background:#fef2f2;color:#991b1b;border:1px solid #fca5a5}
        </style></head><body><div class="card">
        <h1>🔐 Mideye Login</h1>
        <p class="sub">Enter your phone number to authenticate.</p>
        {{body}}
        </div></body></html>
        """;
}
