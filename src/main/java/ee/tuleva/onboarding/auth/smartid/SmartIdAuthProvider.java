package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmartIdAuthProvider implements AuthProvider {

  // Smart-ID's own server-side session TTL is ~90 s (the app warns the user at ~60 s
  // that 30 s remain). We give 120 s here to cover SK's window + the poll/write-back
  // roundtrip; anything shorter would reject users SK still accepts.
  private static final Duration GRANT_TTL = Duration.ofSeconds(120);

  private final GenericSessionStore genericSessionStore;
  private final PrincipalService principalService;
  private final Clock clock;

  @Override
  public boolean supports(GrantType grantType) {
    return SMART_ID.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(String authenticationHash) {
    var session =
        genericSessionStore
            .get(SmartIdSession.class)
            .orElseThrow(SmartIdSessionNotFoundException::new);

    if (!session.getAuthenticationHash().getHashInBase64().equals(authenticationHash)) {
      throw new SmartIdSessionNotFoundException();
    }

    if (Instant.now(clock).isAfter(session.getCreatedAt().plus(GRANT_TTL))) {
      throw new SmartIdSessionNotFoundException();
    }

    if (session.getErrorCode() != null) {
      throw new SmartIdException(ofSingleError(session.getErrorCode(), session.getErrorMessage()));
    }

    if (session.getPerson() == null) {
      throw new AuthNotCompleteException();
    }

    return principalService.getFrom(session.getPerson(), Map.of(GRANT_TYPE, SMART_ID.name()));
  }
}
