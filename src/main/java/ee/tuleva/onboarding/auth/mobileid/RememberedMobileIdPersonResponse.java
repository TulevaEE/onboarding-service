package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.auth.principal.Names.formatted;

public record RememberedMobileIdPersonResponse(String firstName) {

  static RememberedMobileIdPersonResponse from(RememberedMobileIdPerson person) {
    return new RememberedMobileIdPersonResponse(formatted(person.firstName()));
  }
}
