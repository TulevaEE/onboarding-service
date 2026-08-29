package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;

import ee.tuleva.onboarding.analytics.FundUnitCounts;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AuthoritativeUnitsSource {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundUnitCounts fundUnitCounts;
  private final NavLedgerRepository navLedgerRepository;

  Optional<BigDecimal> resolve(TulevaFund fund, LocalDate navDate) {
    if (fund.isSavingsFund()) {
      return Optional.of(
          navLedgerRepository.getSystemAccountBalanceBefore(accountNameFor(fund), endOf(navDate)));
    }
    return fundUnitCounts.totalUnitsAsOf(fund.getIsin(), navDate);
  }

  private static String accountNameFor(TulevaFund fund) {
    return FUND_UNITS_OUTSTANDING.getAccountName(fund);
  }

  private static Instant endOf(LocalDate date) {
    return date.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant();
  }
}
