package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.ALL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.BLACKROCK_ADJUSTMENT_AFTER_REBATE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REBATE_ADJUSTMENT_DEADLINE_WORKING_DAYS;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_REBATE;
import static ee.tuleva.onboarding.ledger.SystemAccount.BLACKROCK_ADJUSTMENT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.ledger.LedgerEntryAmount;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.SystemAccount;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class BlackrockAdjustmentAfterRebateChecker {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final NavLedgerRepository navLedgerRepository;
  private final InvestmentParameterRepository parameterRepository;
  private final PublicHolidays publicHolidays;
  private final int workingDaysReportedAfterDeadline;

  BlackrockAdjustmentAfterRebateChecker(
      NavLedgerRepository navLedgerRepository,
      InvestmentParameterRepository parameterRepository,
      PublicHolidays publicHolidays,
      @Value("${investment.fee-check.unadjusted-rebate-reported-working-days:5}")
          int workingDaysReportedAfterDeadline) {
    this.navLedgerRepository = navLedgerRepository;
    this.parameterRepository = parameterRepository;
    this.publicHolidays = publicHolidays;
    this.workingDaysReportedAfterDeadline = workingDaysReportedAfterDeadline;
  }

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    var deadlineWorkingDays = deadlineWorkingDays(checkDate);
    var unadjustedRebates =
        overdueRebatesStillReported(fund, checkDate, deadlineWorkingDays).stream()
            .filter(rebate -> !receivableSetAfter(fund, rebate))
            .toList();
    if (unadjustedRebates.isEmpty()) {
      return List.of(FeeCheckFinding.pass(fund, BLACKROCK_ADJUSTMENT_AFTER_REBATE, ALL));
    }
    var receivable =
        navLedgerRepository.getSystemAccountBalance(BLACKROCK_ADJUSTMENT.getAccountName(fund));
    return unadjustedRebates.stream()
        .map(rebate -> unadjusted(fund, rebate, workingDaysSince(rebate, checkDate), receivable))
        .toList();
  }

  private int deadlineWorkingDays(LocalDate checkDate) {
    return parameterRepository
        .findLatestValue(REBATE_ADJUSTMENT_DEADLINE_WORKING_DAYS, checkDate)
        .intValueExact();
  }

  private boolean receivableSetAfter(TulevaFund fund, LedgerEntryAmount rebate) {
    return navLedgerRepository.existsTransactionRecordedAfter(
        BLACKROCK_ADJUSTMENT.getAccountName(fund),
        ADJUSTMENT,
        rebate.transactionId(),
        startOf(bookingDate(rebate)));
  }

  private List<LedgerEntryAmount> overdueRebatesStillReported(
      TulevaFund fund, LocalDate checkDate, int deadlineWorkingDays) {
    var lastWorkingDayReported = deadlineWorkingDays + workingDaysReportedAfterDeadline;
    return navLedgerRepository
        .findEntriesByTransactionTypeBetween(
            SystemAccount.MANAGEMENT_FEE_REBATE.getAccountName(fund),
            MANAGEMENT_FEE_REBATE,
            startOf(firstBookingDayWithElapsedWorkingDaysAtMost(checkDate, lastWorkingDayReported)),
            startOf(firstBookingDayWithElapsedWorkingDaysAtMost(checkDate, deadlineWorkingDays)))
        .stream()
        .filter(BlackrockAdjustmentAfterRebateChecker::isCredit)
        .toList();
  }

  private LocalDate firstBookingDayWithElapsedWorkingDaysAtMost(
      LocalDate checkDate, int workingDays) {
    return Stream.iterate(lastWorkingDayOnOrBefore(checkDate), publicHolidays::previousWorkingDay)
        .skip(workingDays)
        .findFirst()
        .orElseThrow();
  }

  private LocalDate lastWorkingDayOnOrBefore(LocalDate date) {
    return publicHolidays.previousWorkingDay(date.plusDays(1));
  }

  private long workingDaysSince(LedgerEntryAmount rebate, LocalDate checkDate) {
    return publicHolidays.countWorkingDaysBehind(bookingDate(rebate), checkDate);
  }

  private static boolean isCredit(LedgerEntryAmount entry) {
    return entry.amount().signum() < 0;
  }

  private static FeeCheckFinding unadjusted(
      TulevaFund fund, LedgerEntryAmount rebate, long workingDaysSince, BigDecimal receivable) {
    var bookingDate = bookingDate(rebate);
    var received = rebate.amount().negate();
    return new FeeCheckFinding(
        fund,
        BLACKROCK_ADJUSTMENT_AFTER_REBATE,
        ALL,
        WARNING,
        "Rebate of "
            + received.toPlainString()
            + " booked to "
            + fund.name()
            + " on "
            + bookingDate
            + ", but "
            + BLACKROCK_ADJUSTMENT.getAccountName(fund)
            + " has not been set since, "
            + workingDaysSince
            + " working days on, and stands at "
            + receivable.toPlainString()
            + "; if that still includes this rebate, the NAV counts it twice, in cash and in the"
            + " receivable",
        received,
        List.of("rebate=" + bookingDate + "/" + received.toPlainString()),
        Map.of(
            "bookingDate",
            bookingDate.toString(),
            "amount",
            received.toPlainString(),
            "workingDaysSince",
            workingDaysSince,
            "receivableBalance",
            receivable.toPlainString()));
  }

  private static LocalDate bookingDate(LedgerEntryAmount entry) {
    return entry.transactionDate().atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ESTONIAN_ZONE).toInstant();
  }
}
