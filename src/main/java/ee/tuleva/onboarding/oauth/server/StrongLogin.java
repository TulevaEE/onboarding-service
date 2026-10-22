package ee.tuleva.onboarding.oauth.server;

import static java.time.temporal.ChronoUnit.SECONDS;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.Instant;

final class StrongLogin {

  private StrongLogin() {}

  static boolean asThemselvesSince(AuthenticatedPerson person, Instant since) {
    var sinceToTheSecondTheLoginRecords = since.truncatedTo(SECONDS);
    return person.isActingAsSelf()
        && person.getGrantType().filter(GrantType::isStrongLogin).isPresent()
        && person
            .getAuthTime()
            .filter(authTime -> !authTime.isBefore(sinceToTheSecondTheLoginRecords))
            .isPresent();
  }
}
