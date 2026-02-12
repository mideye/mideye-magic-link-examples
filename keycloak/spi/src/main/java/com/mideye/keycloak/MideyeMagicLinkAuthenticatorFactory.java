package com.mideye.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Factory for the Mideye Magic Link Authenticator.
 *
 * <p>Registers the authenticator with Keycloak so it can be used in
 * authentication flows. The authenticator appears as "Mideye Magic Link"
 * in the Keycloak Admin Console when configuring authentication flows.</p>
 *
 * @see MideyeMagicLinkAuthenticator
 */
public class MideyeMagicLinkAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "mideye-magic-link";

    private static final MideyeMagicLinkAuthenticator SINGLETON = new MideyeMagicLinkAuthenticator();

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
                + "Sends a push notification or SMS OTP to the user's phone. "
                + "Configure via MIDEYE_URL and MIDEYE_API_KEY environment variables.";
    }

    @Override
    public String getReferenceCategory() {
        return "mfa";
    }

    @Override
    public boolean isConfigurable() {
        // Configuration is done via environment variables, not Keycloak admin config
        return false;
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
        // No admin-configurable properties — all config via environment variables
        return List.of();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // Nothing to initialize
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
