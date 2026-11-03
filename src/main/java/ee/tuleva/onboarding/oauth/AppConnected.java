package ee.tuleva.onboarding.oauth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;

public record AppConnected(AuthenticatedPerson person, String clientId, List<String> scopes) {}
