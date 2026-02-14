package com.mideye.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/**
 * Factory that registers the Magic Link dashboard REST endpoints under each realm.
 *
 * <p>Once deployed, the endpoints are available at:</p>
 * <pre>
 * GET  /realms/{realm}/mideye-magic-link/dashboard
 * GET  /realms/{realm}/mideye-magic-link/api/events
 * GET  /realms/{realm}/mideye-magic-link/api/stats
 * GET  /realms/{realm}/mideye-magic-link/api/config
 * GET  /realms/{realm}/mideye-magic-link/api/export/events
 * POST /realms/{realm}/mideye-magic-link/api/stats/reset
 * </pre>
 *
 * @see MagicLinkDashboardResource
 */
public class MagicLinkDashboardResourceFactory implements RealmResourceProviderFactory {

    private static final Logger LOG = Logger.getLogger(MagicLinkDashboardResourceFactory.class);

    public static final String PROVIDER_ID = "mideye-magic-link";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new MagicLinkDashboardResource(session);
    }

    @Override
    public void init(Config.Scope config) {
        LOG.info("Mideye Magic Link — dashboard resource registered "
            + "(dashboard at /realms/{realm}/mideye-magic-link/dashboard)");
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
