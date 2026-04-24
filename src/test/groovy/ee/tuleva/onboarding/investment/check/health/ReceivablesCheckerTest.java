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

class ReceivablesCheckerTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);
  private final ReceivablesChecker checker = new ReceivablesChecker();

  @Test
  void noFindingsWhenQuantityDecreasedAndReceivablesExist() {
    var today = List.of(securityPosition("IE001", new BigDecimal("900")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var receivables = List.of(receivablesPosition(new BigDecimal("5000")));

    var findings = checker.check(TUK75, today, previous, receivables, List.of());

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityDecreasedButNoReceivables() {
    var today = List.of(securityPosition("IE001", new BigDecimal("900")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));

    var findings = checker.check(TUK75, today, previous, List.of(), List.of());

    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.fund()).isEqualTo(TUK75);
              assertThat(f.checkType()).isEqualTo(HealthCheckType.RECEIVABLES);
              assertThat(f.severity()).isEqualTo(WARNING);
            });
  }

  @Test
  void noFindingsWhenNoQuantityDecrease() {
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
  void passesWhenReceivablesClearedSinceYesterday() {
    var today = List.of(securityPosition("IE0009FT4LX4", new BigDecimal("9000")));
    var previous = List.of(securityPosition("IE0009FT4LX4", new BigDecimal("9100")));
    var todayReceivables = List.of(receivablesPosition(BigDecimal.ZERO));
    var previousReceivables = List.of(receivablesPosition(new BigDecimal("65036.24")));

    var findings = checker.check(TUK75, today, previous, todayReceivables, previousReceivables);

    assertThat(findings).isEmpty();
  }

  @Test
  void warnsWhenQuantityDecreasedAndYesterdayHadNoOpenReceivables() {
    var today = List.of(securityPosition("IE001", new BigDecimal("900")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var previousReceivables = List.of(receivablesPosition(BigDecimal.ZERO));

    var findings = checker.check(TUK75, today, previous, List.of(), previousReceivables);

    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(WARNING));
  }

  @Test
  void messageListsOffendingIsinsSortedWithDeltas() {
    var today =
        List.of(
            securityPosition("IE00BFG1TM61", new BigDecimal("8081836.30")),
            securityPosition("IE0009FT4LX4", new BigDecimal("17732731.63")));
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
                  .contains("IE0009FT4LX4", "-171871.07", "IE00BFG1TM61", "-50399.35");
              assertThat(f.message().indexOf("IE0009FT4LX4"))
                  .isLessThan(f.message().indexOf("IE00BFG1TM61"));
            });
  }

  @Test
  void messageTruncatesWhenMoreThanTenIsinsChanged() {
    var today = new java.util.ArrayList<FundPosition>();
    var previous = new java.util.ArrayList<FundPosition>();
    for (int i = 0; i < 11; i++) {
      today.add(securityPosition(String.format("IE%010d", i), new BigDecimal("100")));
      previous.add(securityPosition(String.format("IE%010d", i), new BigDecimal("200")));
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
  void warnsWhenQuantityDecreasedAndReceivablesHaveZeroMarketValue() {
    var today = List.of(securityPosition("IE001", new BigDecimal("900")));
    var previous = List.of(securityPosition("IE001", new BigDecimal("1000")));
    var receivables = List.of(receivablesPosition(BigDecimal.ZERO));

    var findings = checker.check(TUK75, today, previous, receivables, List.of());

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

  private FundPosition receivablesPosition(BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(TUK75)
        .accountType(AccountType.RECEIVABLES)
        .accountName("Total receivables of unsettled transactions")
        .marketValue(marketValue)
        .build();
  }
}
