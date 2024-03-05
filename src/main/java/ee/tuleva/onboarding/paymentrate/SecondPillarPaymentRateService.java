package ee.tuleva.onboarding.paymentrate;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.application.Application;
import ee.tuleva.onboarding.mandate.application.ApplicationService;
import ee.tuleva.onboarding.mandate.application.PaymentRateApplicationDetails;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecondPillarPaymentRateService {

  private final BigDecimal DEFAULT_SECOND_PILLAR_PAYMENT_RATE = BigDecimal.valueOf(2);
  private final ApplicationService applicationService;

  public PaymentRates getPaymentRates(Person person) {
    return new PaymentRates(
        getCurrentSecondPillarPaymentRate(person).intValue(),
        getPendingSecondPillarPaymentRate(person).map(BigDecimal::intValue).orElse(null));
  }

  private BigDecimal getCurrentSecondPillarPaymentRate(Person person) {
    return applicationService.getPaymentRateApplications(person).stream()
        .filter(Application::isComplete)
        .max(Comparator.comparing(Application<PaymentRateApplicationDetails>::getCreationTime))
        .map(application -> application.getDetails().getPaymentRate())
        .orElse(DEFAULT_SECOND_PILLAR_PAYMENT_RATE);
  }

  private Optional<BigDecimal> getPendingSecondPillarPaymentRate(Person person) {
    return applicationService.getPaymentRateApplications(person).stream()
        .filter(Application::isPending)
        .map(application -> application.getDetails().getPaymentRate())
        .findFirst();
  }
}
