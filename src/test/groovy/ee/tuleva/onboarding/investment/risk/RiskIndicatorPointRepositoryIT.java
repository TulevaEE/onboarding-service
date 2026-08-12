package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static java.math.BigDecimal.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RiskIndicatorPointRepositoryIT {

  @Autowired private RiskIndicatorPointRepository repository;

  @Test
  void persistsAndReadsBackAPointIncludingItsJsonMetrics() {
    var saved = repository.save(point(LocalDate.of(2026, 6, 30), 4));

    var found = repository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100);

    assertThat(found).hasSize(1);
    assertThat(found.getFirst().getId()).isEqualTo(saved.getId());
    assertThat(found.getFirst().getVolatility()).isEqualByComparingTo("0.154000000000");
    assertThat(found.getFirst().getMetrics()).containsEntry("skew", "-0.5");
    assertThat(found.getFirst().getCreatedAt()).isNotNull();
  }

  @Test
  void allowsANullRiskClassForAnIncompleteWindow() {
    repository.save(point(LocalDate.of(2026, 6, 29), null));

    var found = repository.findByIndicatorTypeAndFundOrderByAsOfDateAsc(SRI, TKF100);

    assertThat(found.getFirst().getRiskClass()).isNull();
  }

  @Test
  void rejectsTwoPointsForTheSameIndicatorFundAndDate() {
    repository.saveAndFlush(point(LocalDate.of(2026, 6, 30), 4));

    assertThatThrownBy(() -> repository.saveAndFlush(point(LocalDate.of(2026, 6, 30), 5)))
        .isInstanceOf(Exception.class);
  }

  @Test
  void filtersByDateRange() {
    repository.save(point(LocalDate.of(2026, 6, 1), 4));
    repository.save(point(LocalDate.of(2026, 6, 30), 5));

    var found =
        repository.findByIndicatorTypeAndFundAndAsOfDateBetweenOrderByAsOfDateAsc(
            SRI, TKF100, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 1));

    assertThat(found).hasSize(1);
    assertThat(found.getFirst().getRiskClass()).isEqualTo(5);
  }

  private static RiskIndicatorPoint point(LocalDate date, Integer riskClass) {
    return RiskIndicatorPoint.builder()
        .indicatorType(SRI)
        .fund(TKF100)
        .asOfDate(date)
        .sourceKeys("MSCI_ACWI")
        .riskClass(riskClass)
        .observationCount(1305)
        .volatility(valueOf(0.154))
        .metrics(Map.of("skew", "-0.5"))
        .build();
  }
}
