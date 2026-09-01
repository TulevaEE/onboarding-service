package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import ee.tuleva.onboarding.notification.email.firstpayment.FirstPaymentAudience;
import ee.tuleva.onboarding.notification.email.firstpayment.FirstThirdPillarPayment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FirstPaymentAudienceProvider implements FirstPaymentAudience {

  private final FirstThirdPillarPaymentRepository firstPaymentRepository;

  @Override
  public Optional<LocalDate> oldestOwnPaymentDate() {
    return firstPaymentRepository.oldestOwnPaymentDate();
  }

  @Override
  public List<FirstThirdPillarPayment> fetchUnemailedFirstPayments(
      LocalDate windowStart, LocalDate adultBirthDateCutoff) {
    return firstPaymentRepository.fetchUnemailedFirstPayments(windowStart, adultBirthDateCutoff);
  }
}
