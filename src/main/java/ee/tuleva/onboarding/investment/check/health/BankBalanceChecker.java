package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.BANK_BALANCE;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class BankBalanceChecker {

  private final BankAccounts bankAccounts;
  private final BankStatementBalances bankStatementBalances;

  List<HealthCheckFinding> check(
      TulevaFund fund, LocalDate navDate, List<FundPosition> cashPositions) {
    return investmentAccount(fund)
        .map(
            account ->
                check(fund, navDate, PositionTotals.total(cashPositions, row -> true), account))
        .orElse(List.of());
  }

  private Optional<BankAccount> investmentAccount(TulevaFund fund) {
    return bankAccounts.findAll(fund).stream()
        .filter(account -> account.matches(fund, FUND_INVESTMENT_EUR))
        .findFirst();
  }

  private List<HealthCheckFinding> check(
      TulevaFund fund, LocalDate navDate, BigDecimal reportCash, BankAccount account) {
    try {
      return bankStatementBalances
          .closingBalance(account, navDate)
          .map(balance -> compare(fund, navDate, reportCash, balance))
          .orElseGet(() -> withoutAClosingBalance(fund, navDate, reportCash, account));
    } catch (RuntimeException e) {
      log.error("Bank statement could not be read: fund={}, navDate={}", fund, navDate, e);
      return List.of(
          finding(fund, NOT_RUN, "Bank statement for %s could not be read".formatted(navDate)));
    }
  }

  private List<HealthCheckFinding> withoutAClosingBalance(
      TulevaFund fund, LocalDate navDate, BigDecimal reportCash, BankAccount account) {
    if (bankStatementBalances.isInsideAStatementClosingLater(account, navDate)) {
      return List.of(
          finding(
              fund,
              WARNING,
              ("Report cash %s on %s not compared with the bank: SEB sent that day inside a"
                      + " catch-up statement (one statement for several days, e.g. after an"
                      + " outage) whose closing balance is for a later day. Check the day's"
                      + " balance in SEB's internet bank")
                  .formatted(euros(reportCash), navDate)));
    }
    return List.of(finding(fund, NOT_RUN, "No bank statement for %s".formatted(navDate)));
  }

  private List<HealthCheckFinding> compare(
      TulevaFund fund, LocalDate navDate, BigDecimal reportCash, BigDecimal closingBalance) {
    var difference = reportCash.subtract(closingBalance).setScale(2, HALF_UP);
    if (difference.signum() == 0) {
      return List.of();
    }
    return List.of(
        finding(
            fund,
            WARNING,
            ("Report cash %s, bank closing balance %s on %s, difference %s, NAV too %s."
                    + " Have SEB correct the report's cash before the NAV")
                .formatted(
                    euros(reportCash),
                    euros(closingBalance),
                    navDate,
                    euros(difference),
                    difference.signum() < 0 ? "low" : "high")));
  }

  private static String euros(BigDecimal amount) {
    return amount.setScale(2, HALF_UP).toPlainString();
  }

  private static HealthCheckFinding finding(
      TulevaFund fund, HealthCheckSeverity severity, String message) {
    return new HealthCheckFinding(fund, BANK_BALANCE, severity, message);
  }
}
