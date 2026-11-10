package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThirdPillarAnalyticsTest {

  @Mock
  private ee.tuleva.onboarding.analytics.transaction.thirdpillar
          .AnalyticsThirdPillarTransactionRepository
      transactionRepository;

  @Mock
  private ee.tuleva.onboarding.analytics.transaction.thirdpillar.FirstThirdPillarPaymentRepository
      firstPaymentRepository;

  @Mock private UnitOwnerRepository unitOwnerRepository;
  @InjectMocks private ThirdPillarAnalytics thirdPillarAnalytics;

  @Test
  void readsTheHoldersFromTheLatestRegistrySnapshot() {
    var holder = new ThirdPillarHolder("38888888888", "First", "Last", "EE");
    given(unitOwnerRepository.findThirdPillarHoldersInLatestSnapshot()).willReturn(List.of(holder));

    assertThat(thirdPillarAnalytics.holdersInLatestSnapshot()).containsExactly(holder);
  }

  @Test
  void readsTheLatestRegistrySnapshotDate() {
    var snapshotDate = LocalDate.of(2026, 11, 5);
    given(unitOwnerRepository.findLatestSnapshotDate()).willReturn(Optional.of(snapshotDate));

    assertThat(thirdPillarAnalytics.latestSnapshotDate()).contains(snapshotDate);
  }

  @Test
  void findsTransactionsReportedOnOrAfterTheGivenCutoff() {
    var cutoff = LocalDate.of(2026, 1, 1);
    var transaction = AnalyticsThirdPillarTransactionFixture.exampleTransaction();
    given(transactionRepository.findByReportingDateGreaterThanEqual(cutoff))
        .willReturn(List.of(transaction));

    assertThat(thirdPillarAnalytics.findTransactionsReportedOnOrAfter(cutoff))
        .containsExactly(transaction);
  }

  @Test
  void findsTheSaversOwnContributionsSinceTheGivenDate() {
    var from = LocalDate.of(2026, 7, 1);
    var contribution =
        new ThirdPillarContribution(LocalDate.of(2026, 7, 10), new BigDecimal("100.00"));
    given(transactionRepository.findOwnContributionsSince("38888888888", from))
        .willReturn(List.of(contribution));

    assertThat(thirdPillarAnalytics.ownContributionsSince("38888888888", from))
        .containsExactly(contribution);
  }
}
