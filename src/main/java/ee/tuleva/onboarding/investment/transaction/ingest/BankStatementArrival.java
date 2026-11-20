package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class BankStatementArrival {

  private final BankAccounts bankAccounts;
  private final BankStatementBalances statementBalances;
  private final PublicHolidays publicHolidays;

  DroppedTrades keepThoseWhoseStatementIsIn(List<DroppedTrade> candidates, LocalDate asOfDate) {
    LocalDate statementDay = bankDayOnOrAfter(asOfDate);
    Map<TulevaFund, Boolean> statementIn = new EnumMap<>(TulevaFund.class);
    Set<TulevaFund> fundsNotChecked = EnumSet.noneOf(TulevaFund.class);
    for (DroppedTrade candidate : candidates) {
      TulevaFund fund = candidate.order().getFund();
      if (!statementIn.containsKey(fund)) {
        try {
          statementIn.put(fund, isStatementIn(fund, statementDay));
        } catch (RuntimeException e) {
          log.error(
              "SEB trade listing check could not read the bank statement: fund={},"
                  + " statementDay={}",
              fund,
              statementDay,
              e);
          statementIn.put(fund, false);
          fundsNotChecked.add(fund);
        }
      }
    }
    List<DroppedTrade> dropped =
        candidates.stream()
            .filter(candidate -> statementIn.get(candidate.order().getFund()))
            .toList();
    return new DroppedTrades(dropped, Set.copyOf(fundsNotChecked));
  }

  private boolean isStatementIn(TulevaFund fund, LocalDate statementDay) {
    BankAccount account =
        bankAccounts.findAll(fund).stream()
            .filter(candidate -> candidate.matches(fund, FUND_INVESTMENT_EUR))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No fund investment bank account: fund=" + fund));
    return statementBalances.closingBalance(account, statementDay).isPresent();
  }

  private LocalDate bankDayOnOrAfter(LocalDate date) {
    return publicHolidays.isWorkingDay(date) ? date : publicHolidays.nextWorkingDay(date);
  }
}
