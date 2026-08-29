package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.analytics.FundUnitCounts;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthoritativeUnitsSourceTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 24);
  private static final Instant END_OF_NAV_DATE =
      NAV_DATE.plusDays(1).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant();

  @Mock FundUnitCounts fundUnitCounts;
  @Mock NavLedgerRepository navLedgerRepository;

  @InjectMocks AuthoritativeUnitsSource source;

  @Test
  void resolvesLedgerBalanceAsOfEndOfNavDateForSavingsFund() {
    given(
            navLedgerRepository.getSystemAccountBalanceBefore(
                eq("FUND_UNITS_OUTSTANDING:TKF100"), eq(END_OF_NAV_DATE)))
        .willReturn(new BigDecimal("8066677.82371"));

    var result = source.resolve(TKF100, NAV_DATE);

    assertThat(result).contains(new BigDecimal("8066677.82371"));
  }

  @Test
  void resolvesFundUnitCountsForPillarTwoFund() {
    given(fundUnitCounts.totalUnitsAsOf("EE3600109435", NAV_DATE))
        .willReturn(Optional.of(new BigDecimal("9000123.45")));

    var result = source.resolve(TUK75, NAV_DATE);

    assertThat(result).contains(new BigDecimal("9000123.45"));
  }

  @Test
  void emptyWhenNoFundUnitCountsForPillarTwoFundOnOrBeforeNavDate() {
    given(fundUnitCounts.totalUnitsAsOf("EE3600109435", NAV_DATE)).willReturn(Optional.empty());

    var result = source.resolve(TUK75, NAV_DATE);

    assertThat(result).isEmpty();
  }
}
