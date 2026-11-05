package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
public class ThirdPillarAnalytics {

  private final AnalyticsThirdPillarTransactionRepository transactionRepository;
  private final UnitOwnerRepository unitOwnerRepository;

  public List<RecentThirdPillarCustomer> holdersInLatestSnapshot() {
    return unitOwnerRepository.findThirdPillarHoldersInLatestSnapshot();
  }

  public List<AnalyticsThirdPillarTransaction> findTransactionsReportedOnOrAfter(LocalDate cutoff) {
    return transactionRepository.findByReportingDateGreaterThanEqual(cutoff);
  }

  public List<ThirdPillarContribution> ownContributionsSince(String personalCode, LocalDate from) {
    return transactionRepository.findOwnContributionsSince(personalCode, from);
  }
}
