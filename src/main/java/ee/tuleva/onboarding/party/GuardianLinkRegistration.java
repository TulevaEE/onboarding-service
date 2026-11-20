package ee.tuleva.onboarding.party;

import java.time.LocalDate;

public sealed interface GuardianLinkRegistration {

  ParentChildLink link();

  record Created(ParentChildLink link) implements GuardianLinkRegistration {}

  record EndDateChanged(ParentChildLink link, LocalDate previousValidUntil)
      implements GuardianLinkRegistration {}

  record Unchanged(ParentChildLink link) implements GuardianLinkRegistration {}
}
