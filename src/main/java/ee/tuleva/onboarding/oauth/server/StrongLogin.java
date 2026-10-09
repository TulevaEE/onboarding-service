package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.GrantType.MOBILE_ID;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static java.time.temporal.ChronoUnit.SECONDS;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

final class StrongLogin {

  private static final Set<String> STRONG_LOGIN_METHODS =
      Set.of(SMART_ID.name(), MOBILE_ID.name(), ID_CARD.name());

  private StrongLogin() {}

  static boolean asThemselvesSince(AuthenticatedPerson person, Instant since) {
    var sinceToTheSecondTheLoginRecords = since.truncatedTo(SECONDS);
    return person.isActingAsSelf()
        && Optional.ofNullable(person.getAttribute(GRANT_TYPE))
            .filter(STRONG_LOGIN_METHODS::contains)
            .isPresent()
        && person
            .getAuthTime()
            .filter(authTime -> !authTime.isBefore(sinceToTheSecondTheLoginRecords))
            .isPresent();
  }
}
