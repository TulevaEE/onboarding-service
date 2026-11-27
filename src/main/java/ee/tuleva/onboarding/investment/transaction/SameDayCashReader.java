package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_SOURCES_DIFFERENCE_SHARE_OF_NAV;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.CASH_SOURCES_DIFFER;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.banking.CurrentDayReport;
import ee.tuleva.onboarding.banking.CurrentDayReport.Booked;
import ee.tuleva.onboarding.banking.CurrentDayReport.Missing;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.transaction.CashSourceReport.SourcesDifference;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoBookedBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoCurrentDayReportToday;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoInvestmentAccount;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NotTodaysRun;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.Unreadable;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class SameDayCashReader {

  private static final ZoneId BANK_DAY_ZONE = ZoneId.of("Europe/Tallinn");

  private final BankAccounts bankAccounts;
  private final BankStatementBalances bankStatementBalances;
  private final FundNavQueryService fundNavQueryService;
  private final InvestmentParameterRepository investmentParameterRepository;
  private final Clock clock;

  CashSourceReport read(
      TulevaFund fund,
      LocalDate asOfDate,
      @Nullable BigDecimal operatorAmount,
      @Nullable BigDecimal reportCash,
      GatewayHold heldBackBy) {
    GatewayCash gateway = isToday(asOfDate) ? gatewayCash(fund, asOfDate) : new NotTodaysRun();
    GatewayHold hold =
        operatorAmount == null && gateway instanceof CurrentDayBalance
            ? heldBackBy
            : GatewayHold.NONE;
    List<CalculationWarning> warnings =
        SourcesDifference.between(gateway, reportCash)
            .flatMap(difference -> differenceWarning(fund, asOfDate, difference))
            .stream()
            .toList();
    return new CashSourceReport(
        sourceOf(operatorAmount, gateway, hold), gateway, reportCash, warnings, hold);
  }

  private static CashSource sourceOf(
      @Nullable BigDecimal operatorAmount, GatewayCash gateway, GatewayHold hold) {
    if (operatorAmount != null) {
      return CashSource.OPERATOR_AMOUNT;
    }
    return gateway instanceof CurrentDayBalance && hold == GatewayHold.NONE
        ? CashSource.GATEWAY_BALANCE
        : CashSource.POSITION_REPORT;
  }

  private boolean isToday(LocalDate asOfDate) {
    return clock.instant().atZone(BANK_DAY_ZONE).toLocalDate().equals(asOfDate);
  }

  private GatewayCash gatewayCash(TulevaFund fund, LocalDate today) {
    return BankAccounts.investmentAccountAmong(bankAccounts.findAll(fund), fund)
        .map(
            account ->
                switch (bankStatementBalances.currentDayReport(account, today)) {
                  case Booked booked ->
                      new CurrentDayBalance(
                          booked.balance(),
                          booked.fetchedAt(),
                          booked.pendingEntryReferences(),
                          booked.bookedTodayEntryReferences());
                  case Missing _ -> new NoCurrentDayReportToday();
                  case CurrentDayReport.NoBookedBalance _ -> new NoBookedBalance();
                  case CurrentDayReport.Unreadable _ -> new Unreadable();
                })
        .orElseGet(NoInvestmentAccount::new);
  }

  private Optional<CalculationWarning> differenceWarning(
      TulevaFund fund, LocalDate asOfDate, SourcesDifference sources) {
    BigDecimal difference = sources.gatewayMinusReportCash();
    BigDecimal shareOfNav =
        investmentParameterRepository.findLatestValue(
            CASH_SOURCES_DIFFERENCE_SHARE_OF_NAV, asOfDate);
    return fundNavQueryService
        .findLatestPublishedNavDateOnOrBefore(fund.getCode(), asOfDate)
        .map(navDate -> fundNavQueryService.findAum(fund.getCode(), navDate))
        .filter(nav -> difference.abs().compareTo(nav.multiply(shareOfNav)) > 0)
        .map(
            nav ->
                new CalculationWarning(
                    CASH_SOURCES_DIFFER,
                    ("⚠️ SEB current-day balance differs from the position report's cash by more"
                            + " than %s%% of NAV: fund=%s, gatewayBalance=%s, reportCash=%s,"
                            + " difference=%s, nav=%s")
                        .formatted(
                            shareOfNav.movePointRight(2).stripTrailingZeros().toPlainString(),
                            fund.name(),
                            sources.gatewayBalance().toPlainString(),
                            sources.reportCash().toPlainString(),
                            difference.toPlainString(),
                            nav.setScale(2, HALF_UP).toPlainString())));
  }
}
