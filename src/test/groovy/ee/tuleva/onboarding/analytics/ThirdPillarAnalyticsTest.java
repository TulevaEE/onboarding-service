package ee.tuleva.onboarding.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillar;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillarRepository;
import java.util.List;
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

  @Mock private AnalyticsRecentThirdPillarRepository recentThirdPillarRepository;
  @InjectMocks private ThirdPillarAnalytics thirdPillarAnalytics;

  @Test
  void mapsRecentCustomersToTheReadModel() {
    var record =
        AnalyticsRecentThirdPillar.builder()
            .personalCode("38888888888")
            .firstName("First")
            .lastName("Last")
            .country("EE")
            .build();
    given(recentThirdPillarRepository.findAll()).willReturn(List.of(record));

    assertThat(thirdPillarAnalytics.recentCustomers())
        .containsExactly(new RecentThirdPillarCustomer("38888888888", "First", "Last", "EE"));
  }
}
