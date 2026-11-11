package ee.tuleva.onboarding.investment.fees;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishedFeeBase {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundNavQueryService fundNavQueryService;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;

  public Optional<BigDecimal> find(TulevaFund fund, LocalDate navDate) {
    return fundNavQueryService
        .findFeeBaseComponentTotal(fund.getCode(), navDate)
        .map(total -> total.add(savingsFundBlackrockAdjustmentMissingFromNavReport(fund, navDate)));
  }

  private BigDecimal savingsFundBlackrockAdjustmentMissingFromNavReport(
      TulevaFund fund, LocalDate navDate) {
    if (!fund.isSavingsFund()) {
      return ZERO;
    }
    BigDecimal balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            SystemAccount.BLACKROCK_ADJUSTMENT.getAccountName(fund),
            navCutoffThatChargedTheFee(fund, navDate));
    return balance == null ? ZERO : balance;
  }

  private Instant navCutoffThatChargedTheFee(TulevaFund fund, LocalDate navDate) {
    return publicHolidays
        .nextWorkingDay(navDate)
        .atTime(fund.getNavCutoffTime())
        .atZone(ESTONIAN_ZONE)
        .toInstant();
  }
}
