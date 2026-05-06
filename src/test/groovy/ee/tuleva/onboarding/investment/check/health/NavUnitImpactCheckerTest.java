package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_UNIT_IMPACT;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NavUnitImpactCheckerTest {

  private final NavUnitImpactChecker checker = new NavUnitImpactChecker();

  @Test
  void noFindingWhenDifferenceBelowThreshold() {
    var findings =
        checker.check(
            TKF100,
            new BigDecimal("100000.30"),
            new BigDecimal("100000.00"),
            new BigDecimal("1000000"));

    assertThat(findings).isEmpty();
  }

  @Test
  void warningWhenUnitDifferenceAffectsNav() {
    // TKF100 navScale=4: 1000000/100000 = 10.0000, 1000000/100001 = 9.9999
    var findings =
        checker.check(
            TKF100, new BigDecimal("100000"), new BigDecimal("100001"), new BigDecimal("1000000"));

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TKF100);
              assertThat(f.checkType()).isEqualTo(NAV_UNIT_IMPACT);
              assertThat(f.severity()).isEqualTo(WARNING);
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
            new BigDecimal("500000000"));

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingWhenAumIsNull() {
    var findings = checker.check(TKF100, new BigDecimal("100000"), new BigDecimal("100001"), null);

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingWhenAumIsZero() {
    var findings =
        checker.check(TKF100, new BigDecimal("100000"), new BigDecimal("100001"), BigDecimal.ZERO);

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingWhenReportedUnitsIsNull() {
    var findings = checker.check(TKF100, null, new BigDecimal("100000"), new BigDecimal("1000000"));

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingWhenAuthoritativeUnitsIsNull() {
    var findings = checker.check(TKF100, new BigDecimal("100000"), null, new BigDecimal("1000000"));

    assertThat(findings).isEmpty();
  }
}
