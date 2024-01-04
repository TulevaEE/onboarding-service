package ee.tuleva.onboarding.paymentrate;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecondPillarPaymentRateService {

  private final BigDecimal DEFAULT_SECOND_PILLAR_PAYMENT_RATE = BigDecimal.valueOf(2);
  private final EpisService episService;

  public BigDecimal getPendingSecondPillarPaymentRate(AuthenticatedPerson authenticatedPerson) {
    return episService.getApplications(authenticatedPerson).stream()
        .filter(
            applicationDTO ->
                applicationDTO.isPaymentRate() && applicationDTO.getStatus().isPending())
        .findFirst()
        .map(ApplicationDTO::getPaymentRate)
        .orElse(DEFAULT_SECOND_PILLAR_PAYMENT_RATE);
  }

  private BigDecimal getCurrentSecondPillarPaymentRate() {
    // TODO: Implement this before 2025
    return DEFAULT_SECOND_PILLAR_PAYMENT_RATE;
  }

  public PaymentRates getPaymentRates(AuthenticatedPerson authenticatedPerson) {
    return new PaymentRates(
        getCurrentSecondPillarPaymentRate().intValue(),
        getPendingSecondPillarPaymentRate(authenticatedPerson).intValue());
  }
}
