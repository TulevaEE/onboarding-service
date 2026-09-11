package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.conversion.PendingExchange;
import ee.tuleva.onboarding.conversion.PendingMandateApplications;
import ee.tuleva.onboarding.pillar.Pillar;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MandatePendingApplications implements PendingMandateApplications {

  private final ApplicationService applicationService;

  @Override
  public List<PendingExchange> getPendingExchanges(Pillar pillar, Person person) {
    return applicationService.getTransferApplications(PENDING, person).stream()
        .filter(application -> Integer.valueOf(pillar.toInt()).equals(application.getPillar()))
        .flatMap(application -> application.getDetails().getExchanges().stream())
        .<PendingExchange>map(ExchangeAdapter::new)
        .toList();
  }

  @Override
  public boolean hasPendingWithdrawals(Person person, Pillar pillar) {
    return applicationService.hasPendingWithdrawals(person, pillar);
  }
}
