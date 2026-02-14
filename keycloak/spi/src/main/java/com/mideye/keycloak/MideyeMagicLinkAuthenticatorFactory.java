package com.mideye.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for the Mideye Magic Link Authenticator.
 *
 * <p>Registers the authenticator with Keycloak and exposes all configuration
 * properties in the Admin Console (Authentication → Flows → ⚙ Settings).</p>
 *
 * <p>Each realm can have its own settings. Environment variables
 * ({@code MIDEYE_URL}, {@code MIDEYE_API_KEY}) serve as fallback for backward
 * compatibility when the Admin Console fields are left empty.</p>
 *
 * @see MideyeMagicLinkAuthenticator
 * @see MagicLinkConfig
 */
public class MideyeMagicLinkAuthenticatorFactory implements AuthenticatorFactory {

    private static final Logger LOG = Logger.getLogger(MideyeMagicLinkAuthenticatorFactory.class);

    public static final String PROVIDER_ID = "mideye-magic-link";

    private static final MideyeMagicLinkAuthenticator SINGLETON = new MideyeMagicLinkAuthenticator();

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES =
        ProviderConfigurationBuilder.create()

            // ── Connection ──────────────────────────────────────
            .property()
                .name(MagicLinkConfig.KEY_MIDEYE_URL)
                .label("Mideye Server URL")
                .helpText("Base URL of the Mideye Server (e.g., https://mideye.example.com:8443). "
                    + "Falls back to MIDEYE_URL environment variable if left empty.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("")
                .add()

            .property()
                .name(MagicLinkConfig.KEY_API_KEY)
                .label("API Key")
                .helpText("API key for the Magic Link endpoint. "
                    + "Falls back to MIDEYE_API_KEY environment variable if left empty.")
                .type(ProviderConfigProperty.PASSWORD)
                .defaultValue("")
                .add()

            // ── Behaviour ───────────────────────────────────────
            .property()
                .name(MagicLinkConfig.KEY_PHONE_ATTRIBUTE)
                .label("Phone number attribute")
                .helpText("Keycloak user attribute that stores the phone number in E.164 format "
                    + "(e.g., +46701234567).")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(MagicLinkConfig.DEFAULT_PHONE_ATTRIBUTE)
                .add()

            .property()
                .name(MagicLinkConfig.KEY_TIMEOUT_SECONDS)
                .label("API timeout (seconds)")
                .helpText("How long to wait for the Mideye API response before timing out. "
                    + "The Magic Link call blocks until the user responds on their phone, "
                    + "so this should be long enough for a user to react (60–180 s).")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(MagicLinkConfig.DEFAULT_TIMEOUT_SECONDS))
                .add()

            .property()
                .name(MagicLinkConfig.KEY_SKIP_TLS_VERIFY)
                .label("Skip TLS verification (testing only!)")
                .helpText("Disable TLS certificate verification for the Mideye API connection. "
                    + "NEVER enable this in production.")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue(String.valueOf(MagicLinkConfig.DEFAULT_SKIP_TLS_VERIFY))
                .add()

            // ── Dashboard / Event log ───────────────────────────
            .property()
                .name(MagicLinkConfig.KEY_EVENT_LOG_MAX_SIZE)
                .label("Event log max size")
                .helpText("Maximum number of recent authentication events kept in memory "
                    + "for the dashboard (per realm). Older events are discarded.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(MagicLinkConfig.DEFAULT_EVENT_LOG_MAX_SIZE))
                .add()

            .property()
                .name(MagicLinkConfig.KEY_EVENT_TTL_HOURS)
                .label("Event TTL (hours)")
                .helpText("Events older than this are pruned from the in-memory log.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(MagicLinkConfig.DEFAULT_EVENT_TTL_HOURS))
                .add()

            .property()
                .name(MagicLinkConfig.KEY_DASHBOARD_ROLE)
                .label("Dashboard role")
                .helpText("Realm role required to access the Magic Link dashboard. "
                    + "Users in the master realm with the 'mideye-magic-link-master' role "
                    + "always have access.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(MagicLinkConfig.DEFAULT_DASHBOARD_ROLE)
                .add()

            .build();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Mideye Magic Link";
    }

    @Override
    public String getHelpText() {
        return "Triggers MFA via the Mideye Server Magic Link API. "
                + "Sends a push notification or SMS to the user's phone and waits for approval. "
                + "Configure the Mideye Server URL and API key below (per realm). "
                + "Dashboard: /realms/{realm}/mideye-magic-link/dashboard";
    }

    @Override
    public String getReferenceCategory() {
        return "mfa";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Mideye Magic Link authenticator factory initialized — "
            + "configure per realm in Authentication → Flows → ⚙ Settings");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Nothing to post-initialize
    }

    @Override
    public void close() {
        // Nothing to clean up
    }
}
