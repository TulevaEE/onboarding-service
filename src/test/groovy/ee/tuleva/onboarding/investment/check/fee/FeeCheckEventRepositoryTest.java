package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.SETTLEMENT_COMPLETENESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class FeeCheckEventRepositoryTest {

  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
  private static final Instant BASE_TIME = Instant.parse("2026-06-03T07:00:00Z");

  @Autowired FeeCheckEventRepository repository;

  private int saved = 0;

  // V1_179 had to drop the unique constraints from all three existing check-event tables because
  // the notifier diffs the two latest rows. This table must not reintroduce one.
  @Test
  void twoRunsOnTheSameDayWithTheSameKeyBothPersist() {
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);
    save(FEE_BASE_COMPLETENESS, ALL, null, FAIL);

    assertThat(repository.findAll()).hasSize(2);
  }

  // The whole anti-noise design rests on these two never seeing each other's rows: a null argument
  // to the fee-month method renders as "= ?" and would match nothing, so a shared method would
  // default every daily check to PASS and re-alert a persisting deviation every single day.
  @Test
  void theDailyQueryOnlySeesRowsWithoutAFeeMonth() {
    save(FEE_BASE_COMPLETENESS, ALL, null, FAIL);
    save(FEE_BASE_COMPLETENESS, ALL, MAY, PASS);

    var found =
        repository.findTop2ByFundAndCheckTypeAndFeeScopeAndFeeMonthIsNullOrderByCreatedAtDesc(
            TUK75, FEE_BASE_COMPLETENESS, ALL);

    assertThat(found).singleElement().satisfies(e -> assertThat(e.getSeverity()).isEqualTo(FAIL));
  }

  @Test
  void theMonthlyQueryOnlySeesRowsForThatFeeMonth() {
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, FAIL);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, JUNE, PASS);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, null, PASS);

    var found =
        repository.findTop2ByFundAndCheckTypeAndFeeScopeAndFeeMonthOrderByCreatedAtDesc(
            TUK75, SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY);

    assertThat(found).singleElement().satisfies(e -> assertThat(e.getFeeMonth()).isEqualTo(MAY));
  }

  // A new month must start its own transition history, or a June failure following a May failure
  // reads as "same severity" and goes silent.
  @Test
  void eachFeeMonthKeepsItsOwnHistory() {
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, PASS);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, MAY, FAIL);
    save(SETTLEMENT_COMPLETENESS, MANAGEMENT, JUNE, FAIL);

    var june =
        repository.findTop2ByFundAndCheckTypeAndFeeScopeAndFeeMonthOrderByCreatedAtDesc(
            TUK75, SETTLEMENT_COMPLETENESS, MANAGEMENT, JUNE);

    assertThat(june).hasSize(1);
  }

  @Test
  void theTwoLatestRowsComeBackNewestFirst() {
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);
    save(FEE_BASE_COMPLETENESS, ALL, null, FAIL);
    save(FEE_BASE_COMPLETENESS, ALL, null, PASS);

    var found =
        repository.findTop2ByFundAndCheckTypeAndFeeScopeAndFeeMonthIsNullOrderByCreatedAtDesc(
            TUK75, FEE_BASE_COMPLETENESS, ALL);

    assertThat(found).extracting(FeeCheckEvent::getSeverity).containsExactly(PASS, FAIL);
  }

  @Test
  void nestedResultDetailSurvivesTheJsonRoundTrip() {
    var detail = Map.<String, Object>of("mismatches", Map.of("2026-05-04", "missing=44980.96"));
    var event = event(FEE_BASE_COMPLETENESS, ALL, null, FAIL);
    event.setResult(detail);

    repository.saveAndFlush(event);

    assertThat(repository.findAll().getFirst().getResult()).isEqualTo(detail);
  }

  // createdAt is set explicitly and distinctly: the notifier orders on it, and rows written in the
  // same run would otherwise be free to come back in either order.
  private void save(
      FeeCheckType checkType,
      FeeCheckScope scope,
      @Nullable LocalDate feeMonth,
      FeeCheckSeverity severity) {
    var event = event(checkType, scope, feeMonth, severity);
    event.setCreatedAt(BASE_TIME.plusSeconds(saved++));
    repository.saveAndFlush(event);
  }

  private FeeCheckEvent event(
      FeeCheckType checkType,
      FeeCheckScope scope,
      @Nullable LocalDate feeMonth,
      FeeCheckSeverity severity) {
    return FeeCheckEvent.builder()
        .fund(TUK75)
        .checkDate(LocalDate.of(2026, 6, 3))
        .feeMonth(feeMonth)
        .checkType(checkType)
        .feeScope(scope)
        .severity(severity)
        .deviationFound(severity == FAIL)
        .result(Map.of())
        .build();
  }
}
