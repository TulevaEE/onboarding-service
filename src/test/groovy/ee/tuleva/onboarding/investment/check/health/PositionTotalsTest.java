package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.position.AccountType.UNITS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PositionTotalsTest {

  @Test
  void takesTheNetAssetsFromTheReportsTotalRow() {
    var positions =
        List.of(position(NAV, "1000000.00"), position(SECURITY, "1.00"), position(CASH, "2.00"));

    assertThat(PositionTotals.aum(positions)).isEqualByComparingTo("1000000.00");
  }

  @Test
  void addsUpAssetsAndLiabilitiesWhenTheTotalRowIsMissing() {
    var positions =
        List.of(
            position(SECURITY, "900000.00"),
            position(CASH, "50000.00"),
            position(RECEIVABLES, "1000.00"),
            position(LIABILITY, "-20000.00"),
            position(UNITS, "777.00"));

    assertThat(PositionTotals.aum(positions)).isEqualByComparingTo("931000.00");
  }

  @Test
  void addsUpAssetsAndLiabilitiesWhenTheTotalRowHasNoAmount() {
    var positions = List.of(position(NAV, null), position(SECURITY, "900000.00"));

    assertThat(PositionTotals.aum(positions)).isEqualByComparingTo("900000.00");
  }

  private static FundPosition position(AccountType type, @Nullable String marketValue) {
    return FundPosition.builder()
        .navDate(LocalDate.of(2026, 10, 20))
        .fund(TUK75)
        .accountType(type)
        .accountName(type.name())
        .marketValue(marketValue == null ? null : new BigDecimal(marketValue))
        .build();
  }
}
