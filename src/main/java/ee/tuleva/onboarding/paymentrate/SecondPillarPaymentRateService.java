package ee.tuleva.onboarding.paymentrate;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.application.Application;
import ee.tuleva.onboarding.mandate.application.ApplicationService;
import ee.tuleva.onboarding.mandate.application.PaymentRateApplicationDetails;
import java.math.BigDecimal;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecondPillarPaymentRateService {

  private final BigDecimal DEFAULT_SECOND_PILLAR_PAYMENT_RATE = BigDecimal.valueOf(2);
  private final ApplicationService applicationService;

  private BigDecimal getCurrentSecondPillarPaymentRate(AuthenticatedPerson authenticatedPerson) {
    return applicationService.getPaymentRateApplications(authenticatedPerson).stream()
        .filter(Application::isComplete)
        .max(Comparator.comparing(Application<PaymentRateApplicationDetails>::getCreationTime))
        .map(application -> application.getDetails().getPaymentRate())
        .orElse(DEFAULT_SECOND_PILLAR_PAYMENT_RATE);
  }

  public BigDecimal getPendingSecondPillarPaymentRate(AuthenticatedPerson authenticatedPerson) {
    return applicationService.getPaymentRateApplications(authenticatedPerson).stream()
        .filter(Application::isPending)
        .map(application -> application.getDetails().getPaymentRate())
        .findFirst()
        .orElse(DEFAULT_SECOND_PILLAR_PAYMENT_RATE);
  }

  public PaymentRates getPaymentRates(AuthenticatedPerson authenticatedPerson) {
    return new PaymentRates(
        getCurrentSecondPillarPaymentRate(authenticatedPerson).intValue(),
        getPendingSecondPillarPaymentRate(authenticatedPerson).intValue());
  }
}
