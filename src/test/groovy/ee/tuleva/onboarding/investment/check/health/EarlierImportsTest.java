package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EarlierImportsTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 20);

  @Test
  void namesEveryBlockedFundWhoseEarlierPositionsStayInUse() {
    var earlierImports =
        new EarlierImports(
            Map.of(
                TUK75, Instant.parse("2026-10-21T06:10:00Z"),
                TUK00, Instant.parse("2026-10-21T06:12:00Z")));

    assertThat(earlierImports.whatBlockingLeft(EnumSet.of(TUK75, TUK00)))
        .isEqualTo(
            "TUK75 positions from 2026-10-21 09:10, TUK00 positions from 2026-10-21 09:12 stay in"
                + " use for NAV");
  }

  @Test
  void saysABlockedFundWithoutEarlierPositionsIsNotImported() {
    assertThat(new EarlierImports(Map.of()).whatBlockingLeft(EnumSet.of(TUK75, TUV100)))
        .isEqualTo("TUK75, TUV100 not imported");
  }

  @Test
  void aFundThatPassesKeepsItsUsualStatusEvenWithEarlierPositions() {
    var earlierImports = new EarlierImports(Map.of(TUK75, Instant.parse("2026-10-21T06:10:00Z")));

    assertThat(earlierImports.importStatus(new HealthCheckResult(TUK75, DATE, List.of())))
        .isEqualTo("imported");
    assertThat(
            earlierImports.importStatus(
                new HealthCheckResult(
                    TUK75,
                    DATE,
                    List.of(new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "unknown ISIN")))))
        .isEqualTo("blocked (earlier positions in use)");
  }
}
