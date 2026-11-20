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
import java.util.HashMap;
import java.util.LinkedHashMap;
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

  private record FundDay(TulevaFund fund, LocalDate day) {}

  private enum StatementState {
    IN,
    NOT_YET,
    UNREADABLE
  }

  private final BankAccounts bankAccounts;
  private final BankStatementBalances statementBalances;
  private final PublicHolidays publicHolidays;

  DroppedTrades keepThoseWhoseStatementIsIn(List<DroppedTrade> candidates, LocalDate asOfDate) {
    LocalDate lastBankDay = bankDayOnOrAfter(asOfDate);
    Map<FundDay, StatementState> stateByFundDay = new HashMap<>();
    Map<DroppedTrade, StatementState> coverageByTrade =
        candidates.stream()
            .collect(
                toMap(
                    trade -> trade,
                    trade -> coverage(trade, lastBankDay, stateByFundDay),
                    (first, second) -> first,
                    LinkedHashMap::new));
    List<DroppedTrade> covered =
        coverageByTrade.entrySet().stream()
            .filter(entry -> entry.getValue() == StatementState.IN)
            .map(Map.Entry::getKey)
            .toList();
    Set<TulevaFund> fundsNotChecked =
        coverageByTrade.entrySet().stream()
            .filter(entry -> entry.getValue() == StatementState.UNREADABLE)
            .map(entry -> entry.getKey().order().getFund())
            .collect(toUnmodifiableSet());
    return new DroppedTrades(covered, fundsNotChecked);
  }

  private StatementState coverage(
      DroppedTrade trade, LocalDate lastBankDay, Map<FundDay, StatementState> stateByFundDay) {
    TulevaFund fund = trade.order().getFund();
    List<StatementState> states =
        bankDayOnOrAfter(trade.settlementDate())
            .datesUntil(lastBankDay.plusDays(1))
            .filter(publicHolidays::isWorkingDay)
            .map(
                day ->
                    stateByFundDay.computeIfAbsent(
                        new FundDay(fund, day), key -> stateOf(key.fund(), key.day())))
            .toList();
    if (states.contains(StatementState.UNREADABLE)) {
      return StatementState.UNREADABLE;
    }
    return states.contains(StatementState.NOT_YET) ? StatementState.NOT_YET : StatementState.IN;
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
