package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.pillar.Pillar;
import java.util.List;

public interface PendingMandateApplications {

  List<PendingExchange> getPendingExchanges(Pillar pillar, Person person);

  boolean hasPendingWithdrawals(Person person, Pillar pillar);
}
