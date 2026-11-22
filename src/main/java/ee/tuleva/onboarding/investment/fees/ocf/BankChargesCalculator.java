package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.SCALE;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.BANK_CHARGES_NOT_IN_LEDGER;
import static ee.tuleva.onboarding.investment.fees.ocf.OcfGap.BANK_CHARGES_WITHOUT_AVERAGE_AUM;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.fees.ocf.OcfCalculationService.TransactionCost;
import ee.tuleva.onboarding.ledger.BankCharges;
import ee.tuleva.onboarding.ledger.BankChargesRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Slf4j
@NullMarked
@Component
@RequiredArgsConstructor
class BankChargesCalculator {

  private final BankChargesRepository bankChargesRepository;
  private final FundNavQueryService fundNavQueryService;

  BankChargesCost costOver(TulevaFund fund, TransactionCost transactionWindow) {
    var windowStart = transactionWindow.windowStart();
    var windowEnd = transactionWindow.windowEnd();
    if (windowStart.isAfter(windowEnd)) {
      return new BankChargesCost(ZERO, windowStart, new BankCharges(ZERO, ZERO), List.of());
    }
    var coveredFrom =
        bankChargesRepository
            .statementsInLedgerFrom(fund)
            .map(firstStatementMonth -> laterOf(firstStatementMonth, windowStart))
            .filter(start -> !start.isAfter(windowEnd));
    if (coveredFrom.isEmpty()) {
      log.warn(
          "Bank statements not in the ledger, bank charges unknown: fund={}, windowStart={},"
              + " windowEnd={}",
          fund.getCode(),
          windowStart,
          windowEnd);
      return new BankChargesCost(ZERO, null, null, List.of(BANK_CHARGES_NOT_IN_LEDGER));
    }
    var start = coveredFrom.get();
    var charges = bankChargesRepository.chargedBetween(fund, start, windowEnd);
    if (charges.total().signum() == 0) {
      return new BankChargesCost(ZERO, start, charges, List.of());
    }
    var averageAum =
        start.equals(windowStart)
            ? transactionWindow.averageAum()
            : averageAumFrom(fund, start, transactionWindow.navDates());
    if (averageAum.signum() <= 0) {
      log.warn(
          "Bank charges without an average AUM to divide by, the rate resolves to zero: fund={},"
              + " windowStart={}, charges={}",
          fund.getCode(),
          start,
          charges.total());
      return new BankChargesCost(ZERO, start, charges, List.of(BANK_CHARGES_WITHOUT_AVERAGE_AUM));
    }
    return new BankChargesCost(
        AnnualisedRate.of(start, windowEnd, charges.total(), averageAum),
        start,
        charges,
        List.of());
  }

  private BigDecimal averageAumFrom(TulevaFund fund, LocalDate start, List<LocalDate> navDates) {
    var navDatesCovered = navDates.stream().filter(date -> !date.isBefore(start)).toList();
    if (navDatesCovered.isEmpty()) {
      return ZERO;
    }
    return navDatesCovered.stream()
        .map(date -> fundNavQueryService.findAum(fund.getCode(), date))
        .reduce(ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(navDatesCovered.size()), SCALE, HALF_UP);
  }

  private static LocalDate laterOf(LocalDate one, LocalDate other) {
    return one.isAfter(other) ? one : other;
  }
}
