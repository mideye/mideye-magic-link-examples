package com.mideye.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

/**
 * Keycloak Authenticator that calls the Mideye Magic Link API for MFA.
 *
 * <p>After the user has authenticated with username/password, this authenticator
 * calls the Mideye Server Magic Link API to trigger a push notification or
 * SMS OTP to the user's phone. The API blocks until the user responds.</p>
 *
 * <h3>Configuration</h3>
 * <p>All settings are configured per-realm in the Keycloak Admin Console
 * (Authentication → Flows → Mideye Magic Link → ⚙ Settings). Environment
 * variables ({@code MIDEYE_URL}, {@code MIDEYE_API_KEY}) serve as fallback
 * for backward compatibility.</p>
 *
 * <h3>Dashboard</h3>
 * <p>Recent authentication events are recorded in an in-memory cache and
 * visible on the dashboard at {@code /realms/{realm}/mideye-magic-link/dashboard}.</p>
 *
 * @see MideyeMagicLinkAuthenticatorFactory
 * @see MagicLinkConfig
 */
public class MideyeMagicLinkAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(MideyeMagicLinkAuthenticator.class);

    private static final String ACCEPTED_RESPONSE = "TOUCH_ACCEPTED";

    /** Counter for periodic event pruning (~every 50 authentications). */
    private volatile int authCounter = 0;
    private static final int PRUNE_INTERVAL = 50;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String realmId = context.getRealm().getId();
        MagicLinkEventCache eventCache = MagicLinkEventCache.getInstance(realmId);
        MagicLinkConfig config = loadConfig(context);

        // Apply event log config (picks up runtime changes from Admin Console)
        eventCache.applyConfig(config.getEventLogMaxSize(), config.getEventTtlHours());

        // Periodic pruning of old events
        if (++authCounter % PRUNE_INTERVAL == 0) {
            eventCache.pruneOldEvents();
        }

        UserModel user = context.getUser();
        if (user == null) {
            LOG.error("Mideye MFA: No user in authentication context");
            eventCache.recordEvent(null, null, "error", null,
                getClientIp(context), 0, "No user in context");
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        // Validate configuration
        if (!config.isConfigured()) {
            LOG.error("Mideye MFA: Not configured (URL or API key missing). "
                + "Configure via Admin Console: Authentication → Flows → ⚙ Settings");
            eventCache.recordEvent(user.getUsername(), null, "not_configured", null,
                getClientIp(context), 0, "URL or API key not configured");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // Get the user's phone number
        String phoneNumber = getUserPhoneNumber(user, config.getPhoneAttribute());
        if (phoneNumber == null || phoneNumber.isBlank()) {
            LOG.warnf("Mideye MFA: User '%s' has no phone number in attribute '%s'",
                    user.getUsername(), config.getPhoneAttribute());
            eventCache.recordEvent(user.getUsername(), null, "no_phone", null,
                getClientIp(context), 0,
                "No phone number in attribute '" + config.getPhoneAttribute() + "'");
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        LOG.infof("Mideye MFA: Initiating authentication for user '%s' (phone: %s)",
                user.getUsername(), maskPhoneNumber(phoneNumber));

        // Call the Magic Link API
        long startTime = System.currentTimeMillis();
        try {
            String responseCode = callMagicLinkApi(
                config.getMideyeUrl(), config.getApiKey(), phoneNumber,
                config.getTimeoutSeconds(), config.isSkipTlsVerify());

            long durationMs = System.currentTimeMillis() - startTime;

            if (ACCEPTED_RESPONSE.equals(responseCode)) {
                LOG.infof("Mideye MFA: Authentication ACCEPTED for user '%s' (%d ms)",
                    user.getUsername(), durationMs);
                eventCache.recordEvent(user.getUsername(), maskPhoneNumber(phoneNumber),
                    "success", responseCode, getClientIp(context), durationMs, null);
                context.success();
            } else {
                LOG.warnf("Mideye MFA: Authentication REJECTED for user '%s' (code: %s, %d ms)",
                        user.getUsername(), responseCode, durationMs);

                String outcome = isTimeoutResponse(responseCode) ? "timeout" : "rejected";
                eventCache.recordEvent(user.getUsername(), maskPhoneNumber(phoneNumber),
                    outcome, responseCode, getClientIp(context), durationMs, null);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            LOG.errorf(e, "Mideye MFA: Error calling Magic Link API for user '%s' (%d ms)",
                user.getUsername(), durationMs);

            String outcome = isTimeoutException(e) ? "timeout" : "error";
            eventCache.recordEvent(user.getUsername(), maskPhoneNumber(phoneNumber),
                outcome, null, getClientIp(context), durationMs, e.getMessage());
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    /**
     * Load config from the Keycloak authenticator config (Admin Console).
     */
    MagicLinkConfig loadConfig(AuthenticationFlowContext context) {
        try {
            AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
            if (configModel != null && configModel.getConfig() != null) {
                return MagicLinkConfig.fromMap(configModel.getConfig());
            }
        } catch (Exception e) {
            LOG.debug("Mideye MFA: Could not load authenticator config, using defaults", e);
        }
        return new MagicLinkConfig();
    }

    /**
     * Calls the Mideye Magic Link API and returns the response code.
     */
    String callMagicLinkApi(String baseUrl, String apiKey, String phoneNumber,
                            int timeoutSeconds, boolean skipTlsVerify) throws Exception {
        String encodedPhone = URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8);
        String url = baseUrl + "/api/sfwa/auth?msisdn=" + encodedPhone;

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));

        if (skipTlsVerify) {
            LOG.warn("Mideye MFA: TLS certificate verification is DISABLED (testing only!)");
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllCerts()}, new java.security.SecureRandom());
            clientBuilder.sslContext(sslContext);
        }

        HttpClient client = clientBuilder.build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("api-key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build();

        LOG.debugf("Mideye MFA: Calling API: GET %s", baseUrl + "/api/sfwa/auth?msisdn=***");

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String body = response.body();

        LOG.debugf("Mideye MFA: Response status=%d body=%s", statusCode, body);

        if (statusCode != 200) {
            throw new RuntimeException("Mideye API returned HTTP " + statusCode + ": " + body);
        }

        return parseResponseCode(body);
    }

    /**
     * Parses the "code" field from the JSON response.
     */
    static String parseResponseCode(String json) {
        if (json == null || json.isBlank()) {
            return "UNKNOWN";
        }
        String marker = "\"code\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "UNKNOWN";
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return "UNKNOWN";
        }
        return json.substring(start, end);
    }

    /**
     * Gets the user's phone number from the configured Keycloak user attribute.
     */
    static String getUserPhoneNumber(UserModel user, String attributeName) {
        List<String> values = user.getAttributes().get(attributeName);
        if (values != null && !values.isEmpty()) {
            return values.getFirst();
        }
        return null;
    }

    /**
     * Masks a phone number for logging and dashboard display (shows last 4 digits only).
     */
    static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    private String getClientIp(AuthenticationFlowContext context) {
        try {
            return context.getConnection().getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTimeoutResponse(String responseCode) {
        return responseCode != null && (
            responseCode.contains("TIMEOUT") || responseCode.contains("EXPIRED"));
    }

    private boolean isTimeoutException(Exception e) {
        return e instanceof java.net.http.HttpTimeoutException
            || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"));
    }

    // --- Authenticator lifecycle methods ---

    @Override
    public void action(AuthenticationFlowContext context) {
        // Not used — authenticate() handles everything synchronously
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // Read phone attribute from realm-level authenticator config
        String phoneAttribute = MagicLinkConfig.DEFAULT_PHONE_ATTRIBUTE;
        try {
            MagicLinkConfig config = realm.getAuthenticatorConfigsStream()
                .filter(cm -> cm.getConfig() != null
                        && cm.getConfig().containsKey(MagicLinkConfig.KEY_MIDEYE_URL))
                .findFirst()
                .map(cm -> MagicLinkConfig.fromMap(cm.getConfig()))
                .orElse(null);
            if (config != null) {
                phoneAttribute = config.getPhoneAttribute();
            }
        } catch (Exception e) {
            // Use default
        }

        String phone = getUserPhoneNumber(user, phoneAttribute);
        return phone != null && !phone.isBlank();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions
    }

    @Override
    public void close() {
        // Nothing to clean up
    }

    /**
     * Trust manager that accepts all certificates.
     * FOR TESTING ONLY — never use in production!
     */
    private static class TrustAllCerts implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
