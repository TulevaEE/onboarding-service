package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNIT_RECONCILIATION;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnitReconciliationCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final UnitReconciliationChecker checker = new UnitReconciliationChecker();

  @Test
  void noFindingWhenDifferenceWithinWarningThreshold() {
    var positions = List.of(unitsPosition(TKF100, new BigDecimal("100000.01000")));
    var authoritative = new BigDecimal("100000.00000");
    var threshold = threshold(TKF100, "0.02", "0.5");

    var findings = checker.check(TKF100, NAV_DATE, positions, authoritative, threshold);

    assertThat(findings).isEmpty();
  }

  @Test
  void warningWhenDifferenceExceedsWarningButWithinFail() {
    var positions = List.of(unitsPosition(TKF100, new BigDecimal("100000.00000")));
    var authoritative = new BigDecimal("100000.10000");
    var threshold = threshold(TKF100, "0.02", "0.5");

    var findings = checker.check(TKF100, NAV_DATE, positions, authoritative, threshold);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TKF100);
              assertThat(f.checkType()).isEqualTo(UNIT_RECONCILIATION);
              assertThat(f.severity()).isEqualTo(WARNING);
              assertThat(f.message())
                  .contains("100000.00000")
                  .contains("100000.10000")
                  .contains("0.10000");
            });
  }

  @Test
  void failWhenDifferenceExceedsFailThreshold() {
    var positions = List.of(unitsPosition(TKF100, new BigDecimal("100000.00000")));
    var authoritative = new BigDecimal("100005.00000");
    var threshold = threshold(TKF100, "0.02", "0.5");

    var findings = checker.check(TKF100, NAV_DATE, positions, authoritative, threshold);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.checkType()).isEqualTo(UNIT_RECONCILIATION);
              assertThat(f.severity()).isEqualTo(FAIL);
            });
  }

  @Test
  void absoluteValueIsUsedSoNegativeDifferencesAreCheckedToo() {
    var positions = List.of(unitsPosition(TKF100, new BigDecimal("100005.00000")));
    var authoritative = new BigDecimal("100000.00000");
    var threshold = threshold(TKF100, "0.02", "0.5");

    var findings = checker.check(TKF100, NAV_DATE, positions, authoritative, threshold);

    assertThat(findings).singleElement().satisfies(f -> assertThat(f.severity()).isEqualTo(FAIL));
  }

  @Test
  void warningWhenFailThresholdIsNullAndDifferenceExceedsWarning() {
    var positions = List.of(unitsPosition(TUK75, new BigDecimal("1000000.00000")));
    var authoritative = new BigDecimal("1000050.00000");
    var threshold = threshold(TUK75, "0", null);

    var findings = checker.check(TUK75, NAV_DATE, positions, authoritative, threshold);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void warningWhenAuthoritativeUnitsAreUnavailable() {
    var positions = List.of(unitsPosition(TKF100, new BigDecimal("100000")));
    var threshold = threshold(TKF100, "0.02", "0.5");

    var findings = checker.check(TKF100, NAV_DATE, positions, null, threshold);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.severity()).isEqualTo(WARNING);
              assertThat(f.message()).contains("authoritative");
            });
  }

  @Test
  void warningWhenThresholdRowIsMissing() {
    var positions = List.of(unitsPosition(TKF100, new BigDecimal("100000")));
    var authoritative = new BigDecimal("100000");

    var findings = checker.check(TKF100, NAV_DATE, positions, authoritative, null);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.severity()).isEqualTo(WARNING);
              assertThat(f.message()).contains("threshold");
            });
  }

  @Test
  void noFindingWhenUnitsPositionListIsEmpty() {
    var threshold = threshold(TKF100, "0.02", "0.5");

    var findings = checker.check(TKF100, NAV_DATE, List.of(), new BigDecimal("100000"), threshold);

    assertThat(findings).isEmpty();
  }

  private FundPosition unitsPosition(TulevaFund fund, BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(fund)
        .accountType(UNITS)
        .accountName("Total outstanding units")
        .quantity(quantity)
        .build();
  }

  private UnitReconciliationThreshold threshold(TulevaFund fund, String warning, String fail) {
    return UnitReconciliationThreshold.builder()
        .fundCode(fund)
        .warningUnits(new BigDecimal(warning))
        .failUnits(fail == null ? null : new BigDecimal(fail))
        .build();
  }
}
