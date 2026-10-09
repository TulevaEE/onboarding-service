package ee.tuleva.onboarding.oauth.server;

import java.time.Instant;
import java.util.List;

record ConnectRequestResponse(String clientName, List<String> scopes, Instant expiresAt) {}
