package com.mideye.keycloak;

import java.util.Map;

/**
 * Configuration holder for the Mideye Magic Link authenticator.
 *
 * <p>All settings are configurable per-realm via the Keycloak Admin Console
 * (Authentication → Flows → Mideye Magic Link → ⚙ Settings). Environment
 * variables serve as fallback for backward compatibility.</p>
 *
 * <h3>Config properties</h3>
 * <ul>
 *   <li>{@code mideye.url} — Base URL of the Mideye Server</li>
 *   <li>{@code mideye.apiKey} — API key for the Magic Link endpoint</li>
 *   <li>{@code mideye.phoneAttribute} — Keycloak user attribute containing the phone number</li>
 *   <li>{@code mideye.timeoutSeconds} — HTTP request timeout for the Magic Link API call</li>
 *   <li>{@code mideye.skipTlsVerify} — Skip TLS certificate verification (testing only)</li>
 *   <li>{@code mideye.eventLogMaxSize} — Max events in the in-memory event log</li>
 *   <li>{@code mideye.eventTtlHours} — Event TTL in hours</li>
 *   <li>{@code mideye.dashboardRole} — Realm role required for dashboard access</li>
 * </ul>
 *
 * @see MideyeMagicLinkAuthenticator
 * @see MideyeMagicLinkAuthenticatorFactory
 */
public class MagicLinkConfig {

    // ── Config property keys ───────────────────────────────────

    public static final String KEY_MIDEYE_URL          = "mideye.url";
    public static final String KEY_API_KEY             = "mideye.apiKey";
    public static final String KEY_PHONE_ATTRIBUTE     = "mideye.phoneAttribute";
    public static final String KEY_TIMEOUT_SECONDS     = "mideye.timeoutSeconds";
    public static final String KEY_SKIP_TLS_VERIFY     = "mideye.skipTlsVerify";
    public static final String KEY_EVENT_LOG_MAX_SIZE  = "mideye.eventLogMaxSize";
    public static final String KEY_EVENT_TTL_HOURS     = "mideye.eventTtlHours";
    public static final String KEY_DASHBOARD_ROLE      = "mideye.dashboardRole";

    // ── Defaults ───────────────────────────────────────────────

    public static final String  DEFAULT_PHONE_ATTRIBUTE     = "phoneNumber";
    public static final int     DEFAULT_TIMEOUT_SECONDS     = 120;
    public static final boolean DEFAULT_SKIP_TLS_VERIFY     = false;
    public static final int     DEFAULT_EVENT_LOG_MAX_SIZE  = 1000;
    public static final int     DEFAULT_EVENT_TTL_HOURS     = 1;
    public static final String  DEFAULT_DASHBOARD_ROLE      = "mideye-magic-link-admin";

    // ── Fields ─────────────────────────────────────────────────

    private String mideyeUrl;
    private String apiKey;
    private String phoneAttribute;
    private int timeoutSeconds;
    private boolean skipTlsVerify;
    private int eventLogMaxSize;
    private int eventTtlHours;
    private String dashboardRole;

    /**
     * Create a config with defaults. API credentials fall back to environment
     * variables ({@code MIDEYE_URL}, {@code MIDEYE_API_KEY}) for backward compatibility.
     */
    public MagicLinkConfig() {
        this.mideyeUrl = envOrDefault("MIDEYE_URL", "");
        this.apiKey = envOrDefault("MIDEYE_API_KEY", "");
        this.phoneAttribute = DEFAULT_PHONE_ATTRIBUTE;
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        this.skipTlsVerify = DEFAULT_SKIP_TLS_VERIFY;
        this.eventLogMaxSize = DEFAULT_EVENT_LOG_MAX_SIZE;
        this.eventTtlHours = DEFAULT_EVENT_TTL_HOURS;
        this.dashboardRole = DEFAULT_DASHBOARD_ROLE;
    }

    /**
     * Parse configuration from the Keycloak authenticator config map.
     * Missing or invalid values fall back to defaults.
     * For API credentials, if the Admin Console value is blank/missing,
     * the corresponding environment variable is used as fallback.
     */
    public static MagicLinkConfig fromMap(Map<String, String> config) {
        MagicLinkConfig c = new MagicLinkConfig();
        if (config == null) return c;

        c.mideyeUrl = parseStringWithEnvFallback(config.get(KEY_MIDEYE_URL), "MIDEYE_URL", "");
        c.apiKey = parseStringWithEnvFallback(config.get(KEY_API_KEY), "MIDEYE_API_KEY", "");
        c.phoneAttribute = parseString(config.get(KEY_PHONE_ATTRIBUTE), DEFAULT_PHONE_ATTRIBUTE);
        c.timeoutSeconds = parseInt(config.get(KEY_TIMEOUT_SECONDS), DEFAULT_TIMEOUT_SECONDS);
        c.skipTlsVerify = parseBool(config.get(KEY_SKIP_TLS_VERIFY), DEFAULT_SKIP_TLS_VERIFY);
        c.eventLogMaxSize = parseInt(config.get(KEY_EVENT_LOG_MAX_SIZE), DEFAULT_EVENT_LOG_MAX_SIZE);
        c.eventTtlHours = parseInt(config.get(KEY_EVENT_TTL_HOURS), DEFAULT_EVENT_TTL_HOURS);
        c.dashboardRole = parseString(config.get(KEY_DASHBOARD_ROLE), DEFAULT_DASHBOARD_ROLE);

        return c;
    }

    // ── Getters ────────────────────────────────────────────────

    public String getMideyeUrl()       { return mideyeUrl; }
    public String getApiKey()          { return apiKey; }
    public String getPhoneAttribute()  { return phoneAttribute; }
    public int getTimeoutSeconds()     { return timeoutSeconds; }
    public boolean isSkipTlsVerify()   { return skipTlsVerify; }
    public int getEventLogMaxSize()    { return eventLogMaxSize; }
    public int getEventTtlHours()      { return eventTtlHours; }
    public String getDashboardRole()   { return dashboardRole; }

    /**
     * Whether the authenticator has enough configuration to function
     * (at minimum: URL and API key must be set).
     */
    public boolean isConfigured() {
        return mideyeUrl != null && !mideyeUrl.isBlank()
            && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Serialize to JSON for the dashboard config API endpoint.
     */
    public String toJson() {
        return "{\"mideyeUrl\":\"" + escapeJson(mideyeUrl) + "\""
            + ",\"phoneAttribute\":\"" + escapeJson(phoneAttribute) + "\""
            + ",\"timeoutSeconds\":" + timeoutSeconds
            + ",\"skipTlsVerify\":" + skipTlsVerify
            + ",\"eventLogMaxSize\":" + eventLogMaxSize
            + ",\"eventTtlHours\":" + eventTtlHours
            + ",\"dashboardRole\":\"" + escapeJson(dashboardRole) + "\""
            + ",\"configured\":" + isConfigured()
            + "}";
    }

    // ── Parsing helpers ────────────────────────────────────────

    static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static boolean parseBool(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(value.trim());
    }

    static String parseString(String value, String defaultValue) {
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    static String parseStringWithEnvFallback(String configValue, String envName, String defaultValue) {
        if (configValue != null && !configValue.isBlank()) return configValue.trim();
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) return env.trim();
        return defaultValue;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
