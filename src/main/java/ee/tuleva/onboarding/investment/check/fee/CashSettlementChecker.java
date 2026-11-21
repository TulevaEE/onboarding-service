package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.CASH_SETTLEMENT_OBSERVED;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeMonthBalance;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.OutstandingFees;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CashSettlementChecker {

  private final OutstandingFees outstandingFees;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final PublicHolidays publicHolidays;

  List<FeeCheckFinding> check(TulevaFund fund, LocalDate checkDate) {
    var positionDate = publicHolidays.previousWorkingDay(checkDate);
    var currentMonth = positionDate.withDayOfMonth(1);
    var unpaidMonths =
        outstandingFees
            .monthsOn(feeChargedToFundPolicy.resolverFor(fund, FeeType.MANAGEMENT), positionDate)
            .stream()
            .filter(month -> month.feeMonth().isBefore(currentMonth))
            .filter(month -> month.owed().signum() != 0)
            .toList();
    if (unpaidMonths.isEmpty()) {
      return List.of(FeeCheckFinding.pass(fund, CASH_SETTLEMENT_OBSERVED, MANAGEMENT));
    }
    return List.of(
        new FeeCheckFinding(
            fund,
            CASH_SETTLEMENT_OBSERVED,
            MANAGEMENT,
            WARNING,
            unpaidMonths.stream()
                .map(month -> describe(month, positionDate))
                .collect(joining("; ")),
            unpaidMonths.stream().map(month -> month.owed().abs()).reduce(ZERO, BigDecimal::add),
            unpaidMonths.stream().map(CashSettlementChecker::identifier).toList(),
            Map.of("positionDate", positionDate.toString())));
  }

  private static String describe(FeeMonthBalance month, LocalDate positionDate) {
    if (isUndebited(month)) {
      return "No debit for the "
          + month.feeMonth()
          + " management fee by "
          + positionDate
          + ": "
          + month.owed().toPlainString()
          + " stays owed in the NAV. Ask SEB why it was not debited";
    }
    var debit =
        "The "
            + month.feeMonth()
            + " management fee debit was "
            + month.paid().toPlainString()
            + " against "
            + month.accrued().toPlainString()
            + " accrued: ";
    if (month.owed().signum() < 0) {
      return debit
          + "the fund is owed "
          + month.owed().negate().toPlainString()
          + " back. Ask SEB to refund the excess";
    }
    return debit
        + month.owed().toPlainString()
        + " stays owed in the NAV. Ask SEB to debit the difference";
  }

  private static String identifier(FeeMonthBalance month) {
    return month.feeMonth() + (isUndebited(month) ? " noDebit" : " debitDiffersFromAccrual");
  }

  private static boolean isUndebited(FeeMonthBalance month) {
    return month.paid().signum() == 0;
  }
}
