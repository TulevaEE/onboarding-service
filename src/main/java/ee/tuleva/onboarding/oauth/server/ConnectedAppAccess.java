package ee.tuleva.onboarding.oauth.server;

import java.util.List;
import java.util.UUID;

record ConnectedAppAccess(String clientId, UUID grantId, List<String> scopes) {}
