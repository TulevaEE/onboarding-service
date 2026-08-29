package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillar;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillarRepository;
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
  private final AnalyticsRecentThirdPillarRepository recentThirdPillarRepository;

  public List<RecentThirdPillarCustomer> recentCustomers() {
    return recentThirdPillarRepository.findAll().stream()
        .map(ThirdPillarAnalytics::toRecentCustomer)
        .toList();
  }

  private static RecentThirdPillarCustomer toRecentCustomer(AnalyticsRecentThirdPillar record) {
    return new RecentThirdPillarCustomer(
        record.getPersonalCode(), record.getFirstName(), record.getLastName(), record.getCountry());
  }

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
