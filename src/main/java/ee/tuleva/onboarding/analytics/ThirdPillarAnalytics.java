package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.FirstThirdPillarPaymentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
public class ThirdPillarAnalytics {

  private final AnalyticsThirdPillarTransactionRepository transactionRepository;
  private final FirstThirdPillarPaymentRepository firstPaymentRepository;

  public List<AnalyticsThirdPillarTransaction> findTransactionsReportedOnOrAfter(LocalDate cutoff) {
    return transactionRepository.findByReportingDateGreaterThanEqual(cutoff);
  }

  public Optional<LocalDate> oldestOwnPaymentDate() {
    return firstPaymentRepository.oldestOwnPaymentDate();
  }

  public List<FirstThirdPillarPayment> fetchUnemailedFirstPayments(
      LocalDate windowStart, LocalDate adultBirthDateCutoff) {
    return firstPaymentRepository.fetchUnemailedFirstPayments(windowStart, adultBirthDateCutoff);
  }
}
