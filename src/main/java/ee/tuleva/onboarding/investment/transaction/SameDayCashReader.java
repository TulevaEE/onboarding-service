package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_SOURCES_DIFFERENCE_SHARE_OF_NAV;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.CASH_SOURCES_DIFFER;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoCurrentDayReportToday;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.Unreadable;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
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
      @Nullable BigDecimal reportCash) {
    CashSource source =
        operatorAmount == null ? CashSource.POSITION_REPORT : CashSource.OPERATOR_AMOUNT;
    CashSourceReport report =
        new CashSourceReport(source, gatewayCash(fund), reportCash, List.of());
    BigDecimal difference = report.gatewayMinusReportCash();
    if (difference == null) {
      return report;
    }
    return new CashSourceReport(
        source,
        report.gateway(),
        reportCash,
        differenceWarning(fund, asOfDate, report, difference).stream().toList());
  }

  private Optional<CalculationWarning> differenceWarning(
      TulevaFund fund, LocalDate asOfDate, CashSourceReport report, BigDecimal difference) {
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
                            ((CurrentDayBalance) report.gateway()).balance().toPlainString(),
                            Objects.requireNonNull(report.reportCash()).toPlainString(),
                            difference.toPlainString(),
                            nav.setScale(2, HALF_UP).toPlainString())));
  }

  private GatewayCash gatewayCash(TulevaFund fund) {
    LocalDate today = clock.instant().atZone(BANK_DAY_ZONE).toLocalDate();
    try {
      return bankAccounts.findAll(fund).stream()
          .filter(account -> account.matches(fund, FUND_INVESTMENT_EUR))
          .findFirst()
          .flatMap(account -> bankStatementBalances.currentDayBalance(account, today))
          .<GatewayCash>map(balance -> new CurrentDayBalance(balance.amount(), balance.fetchedAt()))
          .orElseGet(NoCurrentDayReportToday::new);
    } catch (RuntimeException e) {
      log.error("SEB current-day report could not be read: fund={}", fund, e);
      return new Unreadable();
    }
  }
}
