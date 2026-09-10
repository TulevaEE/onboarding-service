package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.LIABILITY_RECOGNITION;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiabilityRecognitionCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);

  private final LiabilityRecognitionChecker checker = new LiabilityRecognitionChecker();

  @Test
  void noFindingsForLiabilityRowsTheLedgerAlreadyAccountsFor() {
    var liabilities =
        List.of(
            liability("Total payables of unsettled transactions", new BigDecimal("-5000.00")),
            liability("Trade Settlement Payable", new BigDecimal("-1200.00")),
            unitFlowLiability("Payables of redeemed units", new BigDecimal("-138440.80")),
            liability("Management Fee Payable", new BigDecimal("-5324.63")),
            liability("Payables to Depository Bank", new BigDecimal("-8115.67")));

    assertThat(checker.check(TUK75, NAV_DATE, liabilities)).isEmpty();
  }

  @Test
  void warnsOnALiabilityRowNoLedgerTreatmentExistsForNamingItAndItsValue() {
    var liabilities =
        List.of(liability("Payables for FX forward settlement", new BigDecimal("-42000.00")));

    assertThat(checker.check(TUK75, NAV_DATE, liabilities))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                LIABILITY_RECOGNITION,
                WARNING,
                "Unrecognised LIABILITY row stays out of trade payables: navDate=2026-04-15,"
                    + " accountName=Payables for FX forward settlement, marketValue=-42000.00"));
  }

  @Test
  void warnsOnTheOtherLiabilitiesRowOurOwnNavOutputWrites() {
    var liabilities = List.of(liability("Liabilities Other", new BigDecimal("-6000.00")));

    assertThat(checker.check(TUK75, NAV_DATE, liabilities))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                LIABILITY_RECOGNITION,
                WARNING,
                "Unrecognised LIABILITY row stays out of trade payables: navDate=2026-04-15,"
                    + " accountName=Liabilities Other, marketValue=-6000.00"));
  }

  @Test
  void warnsOnAnUnrecognisedRowEvenWhenItCarriesTheFundsOwnIsin() {
    var liabilities = List.of(unitFlowLiability("Liabilities Other", new BigDecimal("-6000.00")));

    assertThat(checker.check(TUK75, NAV_DATE, liabilities))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                LIABILITY_RECOGNITION,
                WARNING,
                "Unrecognised LIABILITY row stays out of trade payables: navDate=2026-04-15,"
                    + " accountName=Liabilities Other, marketValue=-6000.00"));
  }

  @Test
  void warnsOncePerUnrecognisedRow() {
    var liabilities =
        List.of(
            liability("Total payables of unsettled transactions", new BigDecimal("-5000.00")),
            liability("Accrued expenses payable", new BigDecimal("-8400.00")),
            liability("Liabilities Other", new BigDecimal("-6000.00")));

    assertThat(checker.check(TUK75, NAV_DATE, liabilities))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                LIABILITY_RECOGNITION,
                WARNING,
                "Unrecognised LIABILITY row stays out of trade payables: navDate=2026-04-15,"
                    + " accountName=Accrued expenses payable, marketValue=-8400.00"),
            new HealthCheckFinding(
                TUK75,
                LIABILITY_RECOGNITION,
                WARNING,
                "Unrecognised LIABILITY row stays out of trade payables: navDate=2026-04-15,"
                    + " accountName=Liabilities Other, marketValue=-6000.00"));
  }

  @Test
  void warnsOnAnUnrecognisedRowThatCarriesNoValueAtAll() {
    var liabilities =
        List.of(
            FundPosition.builder()
                .navDate(NAV_DATE)
                .fund(TUK75)
                .accountType(LIABILITY)
                .accountName("Accrued expenses payable")
                .build());

    assertThat(checker.check(TUK75, NAV_DATE, liabilities))
        .containsExactly(
            new HealthCheckFinding(
                TUK75,
                LIABILITY_RECOGNITION,
                WARNING,
                "Unrecognised LIABILITY row stays out of trade payables: navDate=2026-04-15,"
                    + " accountName=Accrued expenses payable, marketValue=null"));
  }

  @Test
  void noFindingsWhenTheReportCarriesNoLiabilityRows() {
    assertThat(checker.check(TUK75, NAV_DATE, List.of())).isEmpty();
  }

  private FundPosition liability(String accountName, BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(LIABILITY)
        .accountName(accountName)
        .marketValue(marketValue)
        .build();
  }

  private FundPosition unitFlowLiability(String accountName, BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(LIABILITY)
        .accountName(accountName)
        .accountId(TUK75.getIsin())
        .marketValue(marketValue)
        .build();
  }
}
