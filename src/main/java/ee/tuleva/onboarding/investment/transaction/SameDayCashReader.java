package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.CurrentDayBalance;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.NoCurrentDayReportToday;
import ee.tuleva.onboarding.investment.transaction.GatewayCash.Unreadable;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
  private final Clock clock;

  CashSourceReport read(TulevaFund fund, @Nullable BigDecimal operatorAmount) {
    return new CashSourceReport(
        operatorAmount == null ? CashSource.POSITION_REPORT : CashSource.OPERATOR_AMOUNT,
        gatewayCash(fund));
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
