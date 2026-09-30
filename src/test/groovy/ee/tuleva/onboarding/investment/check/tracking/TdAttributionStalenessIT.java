package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.QUARTERLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.TrackingCheckType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TdAttributionStaleness.class)
class TdAttributionStalenessIT {

  private static final YearMonth JUNE = YearMonth.of(2026, 6);
  private static final YearMonth APRIL = YearMonth.of(2026, 4);
  private static final LocalDate JUNE_10 = LocalDate.of(2026, 6, 10);
  private static final LocalDate JULY_1 = LocalDate.of(2026, 7, 1);
  private static final Instant JUNE_CHECKED = Instant.parse("2026-06-10T15:00:00Z");
  private static final Instant JUNE_ATTRIBUTED = Instant.parse("2026-07-06T05:00:00Z");
  private static final Instant JUNE_RECHECKED = Instant.parse("2026-09-28T17:30:00Z");

  @Autowired TdAttributionStaleness staleness;
  @Autowired TrackingDifferenceEventRepository eventRepository;
  @Autowired PeriodicTdAttributionRepository attributionRepository;

  @Test
  void aMonthWithChecksButNoAttributionNeedsARewrite() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isTrue();
  }

  @Test
  void aMonthAttributedAfterItsLastCheckDoesNotNeedARewrite() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);
    storeAttribution(TUK75, JUNE, MONTHLY, JUNE_ATTRIBUTED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isFalse();
  }

  @Test
  void aModelPortfolioRecheckAfterTheAttributionMakesTheMonthNeedARewrite() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);
    storeAttribution(TUK75, JUNE, MONTHLY, JUNE_ATTRIBUTED);
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_RECHECKED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isTrue();
  }

  @Test
  void aBenchmarkModelRecheckAfterTheAttributionMakesTheMonthNeedARewrite() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);
    storeAttribution(TUK75, JUNE, MONTHLY, JUNE_ATTRIBUTED);
    storeEvent(TUK75, JUNE_10, BENCHMARK_MODEL, JUNE_RECHECKED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isTrue();
  }

  @Test
  void aBenchmarkRecheckTheAttributionDoesNotReadLeavesTheMonthAlone() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);
    storeAttribution(TUK75, JUNE, MONTHLY, JUNE_ATTRIBUTED);
    storeEvent(TUK75, JUNE_10, BENCHMARK, JUNE_RECHECKED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isFalse();
  }

  @Test
  void rechecksOfAnotherFundOrAnotherMonthLeaveTheMonthAlone() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);
    storeAttribution(TUK75, JUNE, MONTHLY, JUNE_ATTRIBUTED);
    storeEvent(TUK00, JUNE_10, MODEL_PORTFOLIO, JUNE_RECHECKED);
    storeEvent(TUK75, JULY_1, MODEL_PORTFOLIO, JUNE_RECHECKED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isFalse();
  }

  @Test
  void aQuarterlyAttributionDoesNotCountAsTheMonthsAttribution() {
    storeEvent(TUK75, JUNE_10, MODEL_PORTFOLIO, JUNE_CHECKED);
    storeAttribution(TUK75, APRIL, QUARTERLY, JUNE_ATTRIBUTED);

    assertThat(staleness.needsRewrite(TUK75, JUNE)).isTrue();
  }

  @Test
  void aMonthWithoutChecksIsNeverRewrittenBecauseThereIsNothingToAttribute() {
    assertThat(staleness.needsRewrite(TUK75, JUNE)).isFalse();
  }

  private void storeEvent(
      TulevaFund fund, LocalDate checkDate, TrackingCheckType checkType, Instant writtenAt) {
    eventRepository.save(
        TrackingDifferenceEvent.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(checkType)
            .trackingDifference(ZERO)
            .fundReturn(ZERO)
            .benchmarkReturn(ZERO)
            .createdAt(writtenAt)
            .build());
  }

  private void storeAttribution(
      TulevaFund fund, YearMonth firstMonth, PeriodType periodType, Instant writtenAt) {
    var lastMonth = periodType == MONTHLY ? firstMonth : firstMonth.plusMonths(2);
    attributionRepository.save(
        PeriodicTdAttribution.builder()
            .fund(fund)
            .periodStart(firstMonth.atDay(1))
            .periodEnd(lastMonth.atEndOfMonth())
            .periodType(periodType)
            .fundReturn(ZERO)
            .modelReturn(ZERO)
            .tdGeometric(ZERO)
            .createdAt(writtenAt)
            .build());
  }
}
