package com.mideye.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Keycloak Authenticator that calls the Mideye Magic Link API for MFA.
 *
 * <p>After the user has authenticated with username/password, this authenticator
 * calls the Mideye Server Magic Link API to trigger a push notification or
 * SMS OTP to the user's phone. The API blocks until the user responds.</p>
 *
 * <h3>Configuration</h3>
 * <p>The authenticator reads configuration from environment variables:</p>
 * <ul>
 *   <li>{@code MIDEYE_URL} — Base URL of the Mideye Server (e.g., https://mideye.example.com:8443)</li>
 *   <li>{@code MIDEYE_API_KEY} — API key for the Magic Link endpoint</li>
 *   <li>{@code MIDEYE_PHONE_ATTRIBUTE} — Keycloak user attribute containing the phone number (default: phoneNumber)</li>
 *   <li>{@code MIDEYE_TIMEOUT_SECONDS} — HTTP request timeout in seconds (default: 120)</li>
 *   <li>{@code MIDEYE_SKIP_TLS_VERIFY} — Set to "true" to skip TLS certificate verification for testing (default: false)</li>
 * </ul>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>User completes username/password authentication</li>
 *   <li>This authenticator reads the user's phone number from the configured attribute</li>
 *   <li>Calls GET https://{mideyeServer}:8443/api/sfwa/auth?msisdn={phone}</li>
 *   <li>The API blocks until the user responds on their phone</li>
 *   <li>If response is {@code {"code":"TOUCH_ACCEPTED"}} → authentication succeeds</li>
 *   <li>Any other response → authentication fails</li>
 * </ol>
 *
 * @see MideyeMagicLinkAuthenticatorFactory
 */
public class MideyeMagicLinkAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(MideyeMagicLinkAuthenticator.class);

    private static final String ACCEPTED_RESPONSE = "TOUCH_ACCEPTED";

    // Environment variable names
    private static final String ENV_MIDEYE_URL = "MIDEYE_URL";
    private static final String ENV_MIDEYE_API_KEY = "MIDEYE_API_KEY";
    private static final String ENV_MIDEYE_PHONE_ATTRIBUTE = "MIDEYE_PHONE_ATTRIBUTE";
    private static final String ENV_MIDEYE_TIMEOUT = "MIDEYE_TIMEOUT_SECONDS";
    private static final String ENV_MIDEYE_SKIP_TLS = "MIDEYE_SKIP_TLS_VERIFY";

    // Defaults
    private static final String DEFAULT_PHONE_ATTRIBUTE = "phoneNumber";
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            LOG.error("Mideye MFA: No user in authentication context");
            context.failure(AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        // Read configuration from environment
        String mideyeUrl = getEnv(ENV_MIDEYE_URL);
        String apiKey = getEnv(ENV_MIDEYE_API_KEY);
        String phoneAttribute = getEnvOrDefault(ENV_MIDEYE_PHONE_ATTRIBUTE, DEFAULT_PHONE_ATTRIBUTE);
        int timeoutSeconds = getEnvAsInt(ENV_MIDEYE_TIMEOUT, DEFAULT_TIMEOUT_SECONDS);
        boolean skipTlsVerify = "true".equalsIgnoreCase(getEnvOrDefault(ENV_MIDEYE_SKIP_TLS, "false"));

        // Validate configuration
        if (mideyeUrl == null || mideyeUrl.isBlank()) {
            LOG.error("Mideye MFA: MIDEYE_URL environment variable is not set");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            LOG.error("Mideye MFA: MIDEYE_API_KEY environment variable is not set");
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
            return;
        }

        // Get the user's phone number
        String phoneNumber = getUserPhoneNumber(user, phoneAttribute);
        if (phoneNumber == null || phoneNumber.isBlank()) {
            LOG.warnf("Mideye MFA: User '%s' has no phone number in attribute '%s'",
                    user.getUsername(), phoneAttribute);
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        LOG.infof("Mideye MFA: Initiating authentication for user '%s' (phone: %s)",
                user.getUsername(), maskPhoneNumber(phoneNumber));

        // Call the Magic Link API
        try {
            String responseCode = callMagicLinkApi(mideyeUrl, apiKey, phoneNumber, timeoutSeconds, skipTlsVerify);

            if (ACCEPTED_RESPONSE.equals(responseCode)) {
                LOG.infof("Mideye MFA: Authentication ACCEPTED for user '%s'", user.getUsername());
                context.success();
            } else {
                LOG.warnf("Mideye MFA: Authentication REJECTED for user '%s' (code: %s)",
                        user.getUsername(), responseCode);
                context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Mideye MFA: Error calling Magic Link API for user '%s'", user.getUsername());
            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    /**
     * Calls the Mideye Magic Link API and returns the response code.
     *
     * @param baseUrl         Mideye Server base URL (e.g., https://mideye.example.com:8443)
     * @param apiKey          API key for authentication
     * @param phoneNumber     User's phone number in E.164 format
     * @param timeoutSeconds  HTTP request timeout
     * @param skipTlsVerify   Whether to skip TLS certificate verification
     * @return The response code string (e.g., "TOUCH_ACCEPTED", "TOUCH_REJECTED")
     * @throws Exception if the API call fails
     */
    private String callMagicLinkApi(String baseUrl, String apiKey, String phoneNumber,
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

        // Parse the response: {"code":"TOUCH_ACCEPTED"}
        return parseResponseCode(body);
    }

    /**
     * Parses the "code" field from the JSON response.
     * Simple parser to avoid adding a JSON library dependency.
     */
    private String parseResponseCode(String json) {
        // Expected format: {"code":"TOUCH_ACCEPTED"}
        if (json == null || json.isBlank()) {
            return "UNKNOWN";
        }
        // Find "code":" and extract the value
        String marker = "\"code\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            LOG.warnf("Mideye MFA: Could not parse response code from: %s", json);
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
    private String getUserPhoneNumber(UserModel user, String attributeName) {
        List<String> values = user.getAttributes().get(attributeName);
        if (values != null && !values.isEmpty()) {
            return values.getFirst();
        }
        return null;
    }

    /**
     * Masks a phone number for logging (shows last 4 digits only).
     */
    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    private String getEnv(String name) {
        return System.getenv(name);
    }

    private String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private int getEnvAsInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOG.warnf("Mideye MFA: Invalid integer for %s: '%s', using default %d", name, value, defaultValue);
            }
        }
        return defaultValue;
    }

    // --- Authenticator lifecycle methods ---

    @Override
    public void action(AuthenticationFlowContext context) {
        // Not used — authenticate() handles everything synchronously
    }

    @Override
    public boolean requiresUser() {
        // Yes, we need a user to look up their phone number
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // This authenticator is configured if the user has a phone number attribute
        String phoneAttribute = getEnvOrDefault(ENV_MIDEYE_PHONE_ATTRIBUTE, DEFAULT_PHONE_ATTRIBUTE);
        String phone = getUserPhoneNumber(user, phoneAttribute);
        return phone != null && !phone.isBlank();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions — user must have phone number pre-configured
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
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Accept all
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Accept all
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
