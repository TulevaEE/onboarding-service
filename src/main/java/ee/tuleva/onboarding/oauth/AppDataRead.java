package ee.tuleva.onboarding.oauth;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import java.util.UUID;

public record AppDataRead(
    AuthenticatedPerson person,
    String clientId,
    UUID grantId,
    String endpoint,
    List<String> scopes) {}
