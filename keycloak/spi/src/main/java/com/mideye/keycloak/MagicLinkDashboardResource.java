package com.mideye.keycloak;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resource.RealmResourceProvider;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Keycloak REST resource that provides the Magic Link dashboard and API.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /realms/{realm}/mideye-magic-link/dashboard} — HTML dashboard</li>
 *   <li>{@code GET  /realms/{realm}/mideye-magic-link/api/events} — JSON events</li>
 *   <li>{@code GET  /realms/{realm}/mideye-magic-link/api/stats} — JSON statistics</li>
 *   <li>{@code GET  /realms/{realm}/mideye-magic-link/api/config} — JSON config (no secrets)</li>
 *   <li>{@code GET  /realms/{realm}/mideye-magic-link/api/export/events} — CSV export</li>
 *   <li>{@code POST /realms/{realm}/mideye-magic-link/api/stats/reset} — reset counters</li>
 * </ul>
 *
 * <h3>Authentication</h3>
 * <p>All endpoints require authentication. Access granted if user has:</p>
 * <ol>
 *   <li>Master realm role {@value #MASTER_ADMIN_ROLE} (cross-realm access), or</li>
 *   <li>Per-realm role (default: {@value MagicLinkConfig#DEFAULT_DASHBOARD_ROLE})</li>
 * </ol>
 *
 * @see MagicLinkDashboardResourceFactory
 * @see MagicLinkEventCache
 */
public class MagicLinkDashboardResource implements RealmResourceProvider {

    private static final Logger LOG = Logger.getLogger(MagicLinkDashboardResource.class);

    /** Master realm role for cross-realm dashboard access. */
    static final String MASTER_ADMIN_ROLE = "mideye-magic-link-master";

    private final KeycloakSession session;

    public MagicLinkDashboardResource(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
        // Nothing to clean up
    }

    // ── Helpers ────────────────────────────────────────────────

    private MagicLinkEventCache getCache() {
        return MagicLinkEventCache.getInstance(session.getContext().getRealm().getId());
    }

    private MagicLinkConfig loadConfig() {
        try {
            RealmModel realm = session.getContext().getRealm();
            return realm.getAuthenticatorConfigsStream()
                .filter(cm -> cm.getConfig() != null
                        && cm.getConfig().containsKey(MagicLinkConfig.KEY_MIDEYE_URL))
                .findFirst()
                .map(cm -> MagicLinkConfig.fromMap(cm.getConfig()))
                .orElse(new MagicLinkConfig());
        } catch (Exception e) {
            LOG.debug("MagicLink dashboard: Could not load config, using defaults", e);
            return new MagicLinkConfig();
        }
    }

    private String getRequiredRealmRole() {
        try {
            return loadConfig().getDashboardRole();
        } catch (Exception e) {
            return MagicLinkConfig.DEFAULT_DASHBOARD_ROLE;
        }
    }

    private boolean isMasterAdmin(UserModel user) {
        try {
            RealmModel masterRealm = session.realms().getRealmByName("master");
            if (masterRealm == null) return false;
            RoleModel masterRole = masterRealm.getRole(MASTER_ADMIN_ROLE);
            if (masterRole == null) return false;
            return user.hasRole(masterRole);
        } catch (Exception e) {
            LOG.debug("MagicLink: Error checking master admin role", e);
            return false;
        }
    }

    // ── Authentication ─────────────────────────────────────────

    /**
     * Authenticate for API endpoints (returns 401/403 JSON on failure).
     */
    private Response checkAuth() {
        RealmModel realm = session.getContext().getRealm();

        // 1. Cookie session (browser)
        AuthenticationManager.AuthResult authResult =
            new AppAuthManager().authenticateIdentityCookie(session, realm);

        // 2. Bearer token (API calls from dashboard JS)
        if (authResult == null) {
            authResult = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setUriInfo(session.getContext().getUri())
                .setConnection(session.getContext().getConnection())
                .setHeaders(session.getContext().getRequestHeaders())
                .authenticate();
        }

        // 3. Master realm cookie (admin browsing)
        if (authResult == null) {
            try {
                RealmModel masterRealm = session.realms().getRealmByName("master");
                if (masterRealm != null) {
                    authResult = new AppAuthManager().authenticateIdentityCookie(session, masterRealm);
                }
            } catch (Exception e) {
                LOG.debug("MagicLink: Could not check master realm cookie", e);
            }
        }

        if (authResult == null) {
            String requiredRole = getRequiredRealmRole();
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity("{\"error\":\"Authentication required. "
                    + "Log in and ensure you have the '"
                    + requiredRole + "' realm role or the '"
                    + MASTER_ADMIN_ROLE + "' master realm role.\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        UserModel user = authResult.getUser();

        if (isMasterAdmin(user)) {
            return null; // Access granted
        }

        String requiredRole = getRequiredRealmRole();
        RoleModel role = realm.getRole(requiredRole);

        if (role == null || !user.hasRole(role)) {
            LOG.warnf("MagicLink dashboard: Access denied for user [%s] — "
                + "missing role [%s] (realm) or [%s] (master)",
                user.getUsername(), requiredRole, MASTER_ADMIN_ROLE);
            return Response.status(Response.Status.FORBIDDEN)
                .entity("{\"error\":\"Access denied. "
                    + "Requires realm role '" + escapeJson(requiredRole)
                    + "' or master realm role '" + MASTER_ADMIN_ROLE + "'.\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }

        return null; // Access granted
    }

    /**
     * Authenticate for the HTML dashboard. Redirects to Keycloak login if not
     * authenticated (instead of returning a raw 401 JSON response).
     */
    private Response checkDashboardAuth() {
        RealmModel realm = session.getContext().getRealm();

        AuthenticationManager.AuthResult authResult =
            new AppAuthManager().authenticateIdentityCookie(session, realm);

        if (authResult == null) {
            try {
                RealmModel masterRealm = session.realms().getRealmByName("master");
                if (masterRealm != null) {
                    authResult = new AppAuthManager().authenticateIdentityCookie(session, masterRealm);
                }
            } catch (Exception e) {
                LOG.debug("MagicLink: Could not check master realm cookie for dashboard", e);
            }
        }

        if (authResult == null) {
            // Redirect to Keycloak login using the "magic-link-dashboard" OIDC client
            String base = session.getContext().getUri().getBaseUri().toString();
            if (!base.endsWith("/")) {
                base += "/";
            }
            String dashboardUrl = base
                + "realms/" + realm.getName() + "/mideye-magic-link/dashboard";
            String loginUrl = base
                + "realms/" + realm.getName()
                + "/protocol/openid-connect/auth"
                + "?response_type=code"
                + "&client_id=magic-link-dashboard"
                + "&redirect_uri=" + java.net.URLEncoder.encode(dashboardUrl, StandardCharsets.UTF_8)
                + "&scope=openid";
            LOG.debug("MagicLink dashboard: No session, redirecting to login");
            return Response.status(Response.Status.FOUND)
                .location(URI.create(loginUrl))
                .build();
        }

        UserModel user = authResult.getUser();

        if (isMasterAdmin(user)) {
            return null;
        }

        String requiredRole = getRequiredRealmRole();
        RoleModel role = realm.getRole(requiredRole);

        if (role == null || !user.hasRole(role)) {
            return Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.TEXT_HTML)
                .entity("<!DOCTYPE html><html><head><title>Access Denied</title>"
                    + "<style>body{font-family:system-ui,sans-serif;display:flex;"
                    + "justify-content:center;align-items:center;min-height:100vh;"
                    + "margin:0;background:#1a1a2e;color:#e0e0e0}"
                    + ".card{background:#16213e;padding:2rem 3rem;border-radius:12px;"
                    + "text-align:center;box-shadow:0 4px 24px rgba(0,0,0,.3)}"
                    + "h1{color:#e74c3c;margin-bottom:.5rem}"
                    + "code{background:#0d1b2a;padding:2px 8px;border-radius:4px;"
                    + "color:#f39c12}</style></head>"
                    + "<body><div class='card'>"
                    + "<h1>⛔ Access Denied</h1>"
                    + "<p>Your account <strong>" + escapeHtml(user.getUsername())
                    + "</strong> does not have the required role.</p>"
                    + "<p>You need one of:</p><ul style='text-align:left'>"
                    + "<li>Realm role <code>" + escapeHtml(requiredRole) + "</code> in this realm</li>"
                    + "<li>Master realm role <code>" + MASTER_ADMIN_ROLE + "</code></li>"
                    + "</ul>"
                    + "</div></body></html>")
                .build();
        }

        return null;
    }

    // ── Dashboard HTML ─────────────────────────────────────────

    @GET
    @Path("dashboard")
    @Produces(MediaType.TEXT_HTML)
    public Response dashboard() {
        Response authResponse = checkDashboardAuth();
        if (authResponse != null) return authResponse;

        try {
            InputStream is = getClass().getClassLoader()
                .getResourceAsStream("magic-link-dashboard.html");
            if (is == null) {
                return Response.status(404).entity("Dashboard not found").build();
            }
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Response.ok(html).build();
        } catch (Exception e) {
            LOG.error("Failed to load Magic Link dashboard", e);
            return Response.serverError().entity("Failed to load dashboard").build();
        }
    }

    // ── Events API ─────────────────────────────────────────────

    @GET
    @Path("api/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEvents(@QueryParam("limit") Integer limit) {
        Response authResponse = checkAuth();
        if (authResponse != null) return authResponse;

        int maxConfigured = getCache().getMaxEventLogSize();
        int maxEvents = (limit != null && limit > 0 && limit <= maxConfigured)
            ? limit : Math.min(500, maxConfigured);
        List<MagicLinkEventCache.EventEntry> events = getCache().getRecentEvents(maxEvents);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) json.append(",");
            json.append(events.get(i).toJson());
        }
        json.append("]");

        return Response.ok(json.toString()).build();
    }

    // ── Stats API ──────────────────────────────────────────────

    @GET
    @Path("api/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stats() {
        Response authResponse = checkAuth();
        if (authResponse != null) return authResponse;

        MagicLinkEventCache cache = getCache();

        return Response.ok(
            "{\"totalAttempts\":" + cache.getTotalAttempts()
            + ",\"totalSuccess\":" + cache.getTotalSuccess()
            + ",\"totalRejected\":" + cache.getTotalRejected()
            + ",\"totalErrors\":" + cache.getTotalErrors()
            + ",\"totalNoPhone\":" + cache.getTotalNoPhone()
            + ",\"totalTimeout\":" + cache.getTotalTimeout()
            + ",\"eventLogSize\":" + cache.getEventLogSize()
            + ",\"eventLogMaxSize\":" + cache.getMaxEventLogSize()
            + ",\"eventTtlHours\":" + cache.getEventTtlHours()
            + "}").build();
    }

    @POST
    @Path("api/stats/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetStats() {
        Response authResponse = checkAuth();
        if (authResponse != null) return authResponse;

        getCache().resetStats();
        LOG.info("MagicLink dashboard: Statistics counters reset by admin user");
        return Response.ok("{\"message\":\"Statistics reset\"}").build();
    }

    // ── Config API ─────────────────────────────────────────────

    @GET
    @Path("api/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        Response authResponse = checkAuth();
        if (authResponse != null) return authResponse;

        return Response.ok(loadConfig().toJson()).build();
    }

    // ── Top users API ──────────────────────────────────────────

    @GET
    @Path("api/top-users")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTopUsers(@QueryParam("limit") Integer limit) {
        Response authResponse = checkAuth();
        if (authResponse != null) return authResponse;

        int maxUsers = (limit != null && limit > 0 && limit <= 100) ? limit : 20;
        List<Map.Entry<String, Long>> topUsers = getCache().getTopUsernames(maxUsers);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < topUsers.size(); i++) {
            if (i > 0) json.append(",");
            Map.Entry<String, Long> entry = topUsers.get(i);
            json.append("{\"username\":\"").append(escapeJson(entry.getKey()))
                .append("\",\"count\":").append(entry.getValue())
                .append("}");
        }
        json.append("]");

        return Response.ok(json.toString()).build();
    }

    // ── CSV export ─────────────────────────────────────────────

    @GET
    @Path("api/export/events")
    @Produces("text/csv")
    public Response exportEvents(@QueryParam("limit") Integer limit) {
        Response authResponse = checkAuth();
        if (authResponse != null) return authResponse;

        int maxEvents = (limit != null && limit > 0 && limit <= 5000) ? limit : 5000;
        List<MagicLinkEventCache.EventEntry> events = getCache().getRecentEvents(maxEvents);

        return Response.ok(buildEventsCsv(events))
            .header("Content-Disposition",
                "attachment; filename=\"magic-link-events.csv\"")
            .build();
    }

    // ── CSV formatting (package-private for testability) ───────

    /**
     * Build CSV content for events export.
     */
    static String buildEventsCsv(List<MagicLinkEventCache.EventEntry> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("Timestamp,Username,Phone Number,Outcome,Response Code,IP Address,Duration (ms),Error\n");
        for (MagicLinkEventCache.EventEntry entry : events) {
            csv.append(entry.getTimestamp().toString()).append(",");
            csv.append(escapeCsv(entry.getUsername())).append(",");
            csv.append(escapeCsv(entry.getPhoneNumber())).append(",");
            csv.append(escapeCsv(entry.getOutcome())).append(",");
            csv.append(escapeCsv(entry.getResponseCode())).append(",");
            csv.append(escapeCsv(entry.getIpAddress())).append(",");
            csv.append(entry.getDurationMs()).append(",");
            csv.append(escapeCsv(entry.getErrorMessage())).append("\n");
        }
        return csv.toString();
    }

    // ── String helpers ─────────────────────────────────────────

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
