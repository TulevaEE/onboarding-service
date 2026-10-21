package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_UNIT_IMPACT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NavUnitImpactCheckerTest {

  private final NavUnitImpactChecker checker = new NavUnitImpactChecker();

  @Test
  void aFailureNamesBothUnitCountsSoTheSideThatBrokeIsVisible() {
    var findings =
        checker.check(
            TUK00,
            new BigDecimal("18895375.177"),
            new BigDecimal("18914177.072"),
            new BigDecimal("11421366.17"),
            null);

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                NAV_UNIT_IMPACT,
                FAIL,
                "NAV_SEB=0.60445 vs NAV_EPIS=0.60385 = 0.099400% NAV difference:"
                    + " unitsSeb=18895375.177, unitsEpis=18914177.072,"
                    + " unitsDifference=-18801.895"));
  }

  @Test
  void aFailureSaysWhichUnitCountTheCashAgreesWith() {
    var findings =
        checker.check(
            TUK00,
            new BigDecimal("990000"),
            new BigDecimal("1000000"),
            new BigDecimal("2000000.00"),
            new BigDecimal("999990.500"));

    assertThat(findings)
        .containsExactly(
            new HealthCheckFinding(
                TUK00,
                NAV_UNIT_IMPACT,
                FAIL,
                "NAV_SEB=2.02020 vs NAV_EPIS=2.00000 = 1.010000% NAV difference:"
                    + " unitsSeb=990000, unitsEpis=1000000, unitsDifference=-10000,"
                    + " unitsFromCash=999990.5 (closer to EPIS)"));
  }

  @Test
  void aFailureSaysWhenTheCashAgreesWithSebsUnitCount() {
    var findings =
        checker.check(
            TUK00,
            new BigDecimal("990000"),
            new BigDecimal("1000000"),
            new BigDecimal("2000000.00"),
            new BigDecimal("990001.000"));

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .singleElement()
        .satisfies(
            message -> assertThat(message).endsWith(", unitsFromCash=990001 (closer to SEB)"));
  }

  @Test
  void aFailureNamesNeitherSideWhenTheCashIsNotNearOneOfThem() {
    var findings =
        checker.check(
            TUK00,
            new BigDecimal("990000"),
            new BigDecimal("1000000"),
            new BigDecimal("2000000.00"),
            new BigDecimal("960000.000"));

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .singleElement()
        .satisfies(message -> assertThat(message).endsWith(", unitsFromCash=960000"));
  }

  @Test
  void aFailureNamesNeitherSideWhenTheCashIsAsFarFromEither() {
    var findings =
        checker.check(
            TUK00,
            new BigDecimal("990000"),
            new BigDecimal("1000000"),
            new BigDecimal("2000000.00"),
            new BigDecimal("995000.000"));

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .singleElement()
        .satisfies(message -> assertThat(message).endsWith(", unitsFromCash=995000"));
  }

  @Test
  void anIndeterminateFailureAlsoCarriesTheUnitsFromCash() {
    var findings =
        checker.check(
            TKF100,
            new BigDecimal("100000"),
            new BigDecimal("100001"),
            null,
            new BigDecimal("100001.000"));

    assertThat(findings)
        .extracting(HealthCheckFinding::message)
        .singleElement()
        .satisfies(
            message -> assertThat(message).endsWith(", unitsFromCash=100001 (closer to EPIS)"));
  }

  @Test
  void noFindingWhenDifferenceBelowThreshold() {
    var findings =
        checker.check(
            TKF100,
            new BigDecimal("100000.30"),
            new BigDecimal("100000.00"),
            new BigDecimal("1000000"),
            null);

    assertThat(findings).isEmpty();
  }

  @Test
  void failWhenUnitDifferenceAffectsNav() {
    // TKF100 navScale=4: 1000000/100000 = 10.0000, 1000000/100001 = 9.9999
    var findings =
        checker.check(
            TKF100,
            new BigDecimal("100000"),
            new BigDecimal("100001"),
            new BigDecimal("1000000"),
            null);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TKF100);
              assertThat(f.checkType()).isEqualTo(NAV_UNIT_IMPACT);
              assertThat(f.severity()).isEqualTo(FAIL);
            });
  }

  @Test
  void noFindingWhenDifferenceAboveThresholdButNavUnaffected() {
    // TUK75 navScale=5: 500000000/50000000 = 10.00000, 500000000/50000000.6 = 9.99999...
    // At scale 5 with HALF_UP: 500000000/50000000.6 = 9.99999 (rounds to 9.99999)
    // vs 500000000/50000000 = 10.00000
    // These ARE different — let me use values that round the same
    // For large funds, a 0.6 unit diff on 50M units is ~0.00000012 NAV change, rounds away at scale
    // 5
    var findings =
        checker.check(
            TUK75,
            new BigDecimal("50000000.6"),
            new BigDecimal("50000000.0"),
            new BigDecimal("500000000"),
            null);

    assertThat(findings).isEmpty();
  }

  @Test
  void failsWhenUnitBreakExceedsThresholdButAumIsNull() {
    var findings =
        checker.check(TKF100, new BigDecimal("100000"), new BigDecimal("100001"), null, null);

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.checkType()).isEqualTo(NAV_UNIT_IMPACT);
              assertThat(f.severity()).isEqualTo(FAIL);
              assertThat(f.message()).contains("indeterminate");
            });
  }

  @Test
  void failsWhenUnitBreakExceedsThresholdButAumIsZero() {
    var findings =
        checker.check(
            TKF100, new BigDecimal("100000"), new BigDecimal("100001"), BigDecimal.ZERO, null);

    assertThat(findings).singleElement().satisfies(f -> assertThat(f.severity()).isEqualTo(FAIL));
  }

  @Test
  void noFindingWhenAumIsNullButDifferenceBelowThreshold() {
    var findings =
        checker.check(TKF100, new BigDecimal("100000.30"), new BigDecimal("100000.00"), null, null);

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingWhenReportedUnitsIsNull() {
    var findings =
        checker.check(TKF100, null, new BigDecimal("100000"), new BigDecimal("1000000"), null);

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingWhenAuthoritativeUnitsIsNull() {
    var findings =
        checker.check(TKF100, new BigDecimal("100000"), null, new BigDecimal("1000000"), null);

    assertThat(findings).isEmpty();
  }
}
