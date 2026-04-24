package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayablesCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final PayablesChecker checker = new PayablesChecker();

  @Test
  void noFindingsWhenQuantityIncreasedAndLiabilitiesExist() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var liabilities = List.of(liabilityPosition(new BigDecimal("-5000")));

    var findings = checker.check(TUK75, today, previous, liabilities, List.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityIncreasedButNoLiabilities() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, List.of(), List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TUK75);
              assertThat(f.checkType()).isEqualTo(HealthCheckType.PAYABLES);
              assertThat(f.severity()).isEqualTo(WARNING);
            });
  }

  @Test
  void noFindingsWhenNoQuantityIncrease() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, List.of(), List.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void noFindingsWhenNoPreviousDayData() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, List.of(), List.of(), List.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void passesWhenPayablesClearedSinceYesterday() {
    var today = List.of(securityPosition("IE0009FT4LX4", new BigDecimal("18076473.774")));
    var previous = List.of(securityPosition("IE0009FT4LX4", new BigDecimal("17904602.704")));
    var todayLiabilities = List.of(liabilityPosition(BigDecimal.ZERO));
    var previousLiabilities = List.of(liabilityPosition(new BigDecimal("-1780760.00")));

    var findings = checker.check(TUK75, today, previous, todayLiabilities, previousLiabilities);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityIncreasedAndYesterdayHadNoOpenPayables() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var previousLiabilities = List.of(liabilityPosition(BigDecimal.ZERO));

    var findings = checker.check(TUK75, today, previous, List.of(), previousLiabilities);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void messageListsOffendingIsinsSortedWithDeltas() {
    var today =
        List.of(
            securityPosition("IE00BFG1TM61", new BigDecimal("8182635.00")),
            securityPosition("IE0009FT4LX4", new BigDecimal("18076473.77")));
    var previous =
        List.of(
            securityPosition("IE00BFG1TM61", new BigDecimal("8132235.65")),
            securityPosition("IE0009FT4LX4", new BigDecimal("17904602.70")));

    var findings = checker.check(TUK75, today, previous, List.of(), List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.message())
                  .contains("IE0009FT4LX4", "+171871.07", "IE00BFG1TM61", "+50399.35");
              assertThat(f.message().indexOf("IE0009FT4LX4"))
                  .isLessThan(f.message().indexOf("IE00BFG1TM61"));
            });
  }

  @Test
  void messageTruncatesWhenMoreThanTenIsinsChanged() {
    var today = new java.util.ArrayList<FundPosition>();
    var previous = new java.util.ArrayList<FundPosition>();
    for (int i = 0; i < 11; i++) {
      today.add(securityPosition(String.format("IE%010d", i), new BigDecimal("200")));
      previous.add(securityPosition(String.format("IE%010d", i), new BigDecimal("100")));
    }

    var findings = checker.check(TUK75, today, previous, List.of(), List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.message()).contains("IE0000000000", "IE0000000009", "... (1 more)");
              assertThat(f.message()).doesNotContain("IE0000000010");
            });
  }

  @Test
  void warnsWhenQuantityIncreasedAndLiabilitiesHaveZeroMarketValue() {
    var today = List.of(securityPosition("IE001", new BigDecimal("1100")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var liabilities = List.of(liabilityPosition(BigDecimal.ZERO));

    var findings = checker.check(TUK75, today, previous, liabilities, List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  private FundPosition securityPosition(String isin, BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName("iShares")
        .accountId(isin)
        .quantity(quantity)
        .build();
  }

  private FundPosition liabilityPosition(BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(AccountType.LIABILITY)
        .accountName("Total payables of unsettled transactions")
        .marketValue(marketValue)
        .build();
  }
}
