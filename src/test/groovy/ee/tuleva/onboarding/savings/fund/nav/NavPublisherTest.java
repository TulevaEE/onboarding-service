package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavPublisherTest {

  @Mock private FundValueRepository fundValueRepository;
  @Mock private NavNotifier navNotifier;

  @InjectMocks private NavPublisher navPublisher;

  @Test
  void publish_savesNavAndAumWithPositionReportDate() {
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate yesterday = LocalDate.of(2025, 1, 14);
    Instant calcTime = Instant.parse("2025-01-15T14:00:00Z");

    var result =
        NavCalculationResult.builder()
            .fund(TKF100)
            .calculationDate(today)
            .securitiesValue(new BigDecimal("900000.00"))
            .cashPosition(new BigDecimal("50000.00"))
            .receivables(new BigDecimal("10000.00"))
            .pendingSubscriptions(new BigDecimal("25000.00"))
            .pendingRedemptions(new BigDecimal("10000.00"))
            .managementFeeAccrual(new BigDecimal("52.08"))
            .depotFeeAccrual(new BigDecimal("6.85"))
            .payables(new BigDecimal("5000.00"))
            .blackrockAdjustment(BigDecimal.ZERO)
            .aum(new BigDecimal("969941.07"))
            .unitsOutstanding(new BigDecimal("100000.00000"))
            .navPerUnit(new BigDecimal("9.69941"))
            .positionReportDate(yesterday)
            .priceDate(yesterday)
            .calculatedAt(calcTime)
            .securitiesDetail(List.of())
            .build();

    navPublisher.publish(result);

    ArgumentCaptor<FundValue> captor = forClass(FundValue.class);
    verify(fundValueRepository, times(2)).save(captor.capture());

    var savedValues = captor.getAllValues();

    var navValue = savedValues.get(0);
    assertThat(navValue.key()).isEqualTo("EE0000003283");
    assertThat(navValue.date()).isEqualTo(yesterday);
    assertThat(navValue.value()).isEqualByComparingTo("9.69941");
    assertThat(navValue.provider()).isEqualTo("TULEVA");
    assertThat(navValue.updatedAt()).isEqualTo(calcTime);

    var aumValue = savedValues.get(1);
    assertThat(aumValue.key()).isEqualTo("AUM_EE0000003283");
    assertThat(aumValue.date()).isEqualTo(yesterday);
    assertThat(aumValue.value()).isEqualByComparingTo("969941.07");
    assertThat(aumValue.provider()).isEqualTo("TULEVA");
    assertThat(aumValue.updatedAt()).isEqualTo(calcTime);

    verify(navNotifier).notify(result);
  }
}
