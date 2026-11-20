package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.BankStatementBalances;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.EnumMap;
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

  private enum StatementState {
    IN,
    NOT_YET,
    UNREADABLE
  }

  private final BankAccounts bankAccounts;
  private final BankStatementBalances statementBalances;
  private final PublicHolidays publicHolidays;

  DroppedTrades keepThoseWhoseStatementIsIn(List<DroppedTrade> candidates, LocalDate asOfDate) {
    LocalDate statementDay = bankDayOnOrAfter(asOfDate);
    Map<TulevaFund, StatementState> stateByFund =
        candidates.stream()
            .map(candidate -> candidate.order().getFund())
            .distinct()
            .collect(
                toMap(
                    fund -> fund,
                    fund -> stateOf(fund, statementDay),
                    (first, second) -> first,
                    () -> new EnumMap<>(TulevaFund.class)));
    List<DroppedTrade> dropped =
        candidates.stream()
            .filter(candidate -> stateByFund.get(candidate.order().getFund()) == StatementState.IN)
            .toList();
    Set<TulevaFund> fundsNotChecked =
        stateByFund.entrySet().stream()
            .filter(entry -> entry.getValue() == StatementState.UNREADABLE)
            .map(Map.Entry::getKey)
            .collect(toUnmodifiableSet());
    return new DroppedTrades(dropped, fundsNotChecked);
  }

  private StatementState stateOf(TulevaFund fund, LocalDate statementDay) {
    try {
      return isStatementIn(fund, statementDay) ? StatementState.IN : StatementState.NOT_YET;
    } catch (RuntimeException e) {
      log.error(
          "SEB trade listing check could not read the bank statement: fund={}, statementDay={}",
          fund,
          statementDay,
          e);
      return StatementState.UNREADABLE;
    }
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
