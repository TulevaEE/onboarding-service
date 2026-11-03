package ee.tuleva.onboarding.investment.check.fee;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeBaseValue;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ExpectedFeeBases {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final FundNavQueryService fundNavQueryService;
  private final NavLedgerRepository navLedgerRepository;
  private final PublicHolidays publicHolidays;

  ExpectedFeeBases(
      FundNavQueryService fundNavQueryService,
      NavLedgerRepository navLedgerRepository,
      PublicHolidays publicHolidays) {
    this.fundNavQueryService = fundNavQueryService;
    this.navLedgerRepository = navLedgerRepository;
    this.publicHolidays = publicHolidays;
  }

  Optional<Map<FeeType, BigDecimal>> expectedBases(
      TulevaFund fund, List<FeeBaseValue> bases, LocalDate date) {
    return netBaseBeforeFees(fund, date)
        .map(expected -> bases.stream().collect(toMap(FeeBaseValue::feeType, base -> expected)));
  }

  private Optional<BigDecimal> netBaseBeforeFees(TulevaFund fund, LocalDate date) {
    return fundNavQueryService
        .findFeeBaseComponentTotal(fund.getCode(), date)
        .map(total -> total.add(navFeeBaseBlackrockAdjustment(fund, date)));
  }

  private BigDecimal navFeeBaseBlackrockAdjustment(TulevaFund fund, LocalDate positionReportDate) {
    return fund.isSavingsFund()
        ? blackrockAdjustmentMissingFromNavReport(fund, positionReportDate)
        : ZERO;
  }

  private BigDecimal blackrockAdjustmentMissingFromNavReport(
      TulevaFund fund, LocalDate positionReportDate) {
    var balance =
        navLedgerRepository.getSystemAccountBalanceBefore(
            SystemAccount.BLACKROCK_ADJUSTMENT.getAccountName(fund),
            navCutoffThatChargedTheFee(fund, positionReportDate));
    return balance == null ? ZERO : balance;
  }

  private Instant navCutoffThatChargedTheFee(TulevaFund fund, LocalDate positionReportDate) {
    return publicHolidays
        .nextWorkingDay(positionReportDate)
        .atTime(fund.getNavCutoffTime())
        .atZone(ESTONIAN_ZONE)
        .toInstant();
  }
}
