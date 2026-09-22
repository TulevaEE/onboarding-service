package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class SebStatementFetchingSchedulerTest {

  private static final List<BankAccount> SAVINGS_FUND_ACCOUNTS =
      List.of(
          new BankAccount("EE001234567890123456", DEPOSIT_EUR, TKF100, "gw-test"),
          new BankAccount("EE001234567890123457", WITHDRAWAL_EUR, TKF100, "gw-test"),
          new BankAccount("EE001234567890123458", FUND_INVESTMENT_EUR, TKF100, "gw-test"));

  private static final List<BankAccount> ALL_ACCOUNTS =
      List.of(
          SAVINGS_FUND_ACCOUNTS.get(0),
          SAVINGS_FUND_ACCOUNTS.get(1),
          SAVINGS_FUND_ACCOUNTS.get(2),
          new BankAccount("EE001234567890123459", FUND_INVESTMENT_EUR, TUK75, "gw-test-tuk75"),
          new BankAccount("EE001234567890123460", FUND_INVESTMENT_EUR, TUK00, "gw-test-tuk00"),
          new BankAccount("EE001234567890123461", FUND_INVESTMENT_EUR, TUV100, "gw-test-tuv100"));

  private static final LocalDate YESTERDAY = LocalDate.of(2026, 9, 13);
  private static final LocalDate CATCH_UP_FROM = LocalDate.of(2026, 9, 6);
  private static final LocalDate DAY_BEFORE_YESTERDAY = LocalDate.of(2026, 9, 12);

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T01:00:00Z"), ZoneOffset.UTC);

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private BankAccounts bankAccounts;
  @Mock private StatementCoverage statementCoverage;

  @Test
  void fetchCurrentDayTransactions_publishesEventsOnlyForSavingsFundAccounts() {
    given(bankAccounts.findAll(TKF100)).willReturn(SAVINGS_FUND_ACCOUNTS);
    var scheduler = scheduler();

    scheduler.fetchCurrentDayTransactions();

    for (BankAccount account : SAVINGS_FUND_ACCOUNTS) {
      then(eventPublisher)
          .should()
          .publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
    }
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchCurrentDayTransactions_continuesOnError() {
    given(bankAccounts.findAll(TKF100)).willReturn(SAVINGS_FUND_ACCOUNTS);
    var scheduler = scheduler();
    willThrow(new RuntimeException("Error"))
        .given(eventPublisher)
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(SAVINGS_FUND_ACCOUNTS.get(0)));

    scheduler.fetchCurrentDayTransactions();

    for (BankAccount account : SAVINGS_FUND_ACCOUNTS) {
      then(eventPublisher)
          .should()
          .publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
    }
  }

  @Test
  void fetchEodTransactions_fetchesYesterdaysStatementForEveryAccountThatHasNotReceivedIt() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(any(), eq(YESTERDAY))).willReturn(false);
    given(statementCoverage.missingPeriods(any(), eq(CATCH_UP_FROM), eq(DAY_BEFORE_YESTERDAY)))
        .willReturn(List.of());
    var scheduler = scheduler();

    scheduler.fetchEodTransactions();

    for (BankAccount account : ALL_ACCOUNTS) {
      then(eventPublisher)
          .should()
          .publishEvent(new FetchSebEodTransactionsRequested(account, YESTERDAY));
    }
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchEodTransactions_skipsAccountsWhoseStatementForYesterdayWasAlreadyReceived() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(any(), eq(YESTERDAY))).willReturn(true);
    given(statementCoverage.missingPeriods(any(), eq(CATCH_UP_FROM), eq(DAY_BEFORE_YESTERDAY)))
        .willReturn(List.of());
    var scheduler = scheduler();

    scheduler.fetchEodTransactions();

    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void fetchEodTransactions_continuesWithTheNextAccountWhenAFetchFails() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(any(), eq(YESTERDAY))).willReturn(false);
    given(statementCoverage.missingPeriods(any(), eq(CATCH_UP_FROM), eq(DAY_BEFORE_YESTERDAY)))
        .willReturn(List.of());
    var scheduler = scheduler();
    willThrow(new RuntimeException("503 LBR_SERVICE_UNAVAILABLE"))
        .given(eventPublisher)
        .publishEvent(new FetchSebEodTransactionsRequested(ALL_ACCOUNTS.get(0), YESTERDAY));

    scheduler.fetchEodTransactions();

    for (BankAccount account : ALL_ACCOUNTS) {
      then(eventPublisher)
          .should()
          .publishEvent(new FetchSebEodTransactionsRequested(account, YESTERDAY));
    }
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchEodTransactions_continuesWithTheNextAccountWhenOnlyItsStatementIsNotGeneratedYet() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(any(), eq(YESTERDAY))).willReturn(false);
    given(statementCoverage.missingPeriods(any(), eq(CATCH_UP_FROM), eq(DAY_BEFORE_YESTERDAY)))
        .willReturn(List.of());
    var scheduler = scheduler();
    willThrow(HttpClientErrorException.create(NOT_FOUND, "Not Found", null, null, null))
        .given(eventPublisher)
        .publishEvent(new FetchSebEodTransactionsRequested(ALL_ACCOUNTS.get(0), YESTERDAY));

    scheduler.fetchEodTransactions();

    for (BankAccount account : ALL_ACCOUNTS) {
      then(eventPublisher)
          .should()
          .publishEvent(new FetchSebEodTransactionsRequested(account, YESTERDAY));
    }
  }

  @Test
  void fetchEodTransactions_stopsTheRunWhenTheGatewayAnswersWithAServerError() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(ALL_ACCOUNTS.get(0), YESTERDAY)).willReturn(false);
    var scheduler = scheduler();
    willThrow(HttpServerErrorException.create(SERVICE_UNAVAILABLE, "Unavailable", null, null, null))
        .given(eventPublisher)
        .publishEvent(new FetchSebEodTransactionsRequested(ALL_ACCOUNTS.get(0), YESTERDAY));

    scheduler.fetchEodTransactions();

    then(eventPublisher)
        .should()
        .publishEvent(new FetchSebEodTransactionsRequested(ALL_ACCOUNTS.get(0), YESTERDAY));
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchEodTransactions_stopsTheRunWhenTheGatewayCannotBeReached() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(ALL_ACCOUNTS.get(0), YESTERDAY)).willReturn(false);
    var scheduler = scheduler();
    willThrow(new ResourceAccessException("Read timed out"))
        .given(eventPublisher)
        .publishEvent(new FetchSebEodTransactionsRequested(ALL_ACCOUNTS.get(0), YESTERDAY));

    scheduler.fetchEodTransactions();

    then(eventPublisher)
        .should()
        .publishEvent(new FetchSebEodTransactionsRequested(ALL_ACCOUNTS.get(0), YESTERDAY));
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchEodTransactions_stopsTheRunWhenTheGatewayFailsDuringCatchUp() {
    var first = ALL_ACCOUNTS.get(0);
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.isReceived(first, YESTERDAY)).willReturn(true);
    var gap = new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 11));
    given(statementCoverage.missingPeriods(first, CATCH_UP_FROM, DAY_BEFORE_YESTERDAY))
        .willReturn(List.of(gap));
    var scheduler = scheduler();
    willThrow(new ResourceAccessException("Read timed out"))
        .given(eventPublisher)
        .publishEvent(new FetchSebHistoricTransactionsRequested(first, gap.from(), gap.to()));

    scheduler.fetchEodTransactions();

    then(eventPublisher)
        .should()
        .publishEvent(new FetchSebHistoricTransactionsRequested(first, gap.from(), gap.to()));
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchCurrentDayTransactions_stopsTheRunWhenTheGatewayAnswersWithAServerError() {
    given(bankAccounts.findAll(TKF100)).willReturn(SAVINGS_FUND_ACCOUNTS);
    var scheduler = scheduler();
    willThrow(HttpServerErrorException.create(SERVICE_UNAVAILABLE, "Unavailable", null, null, null))
        .given(eventPublisher)
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(SAVINGS_FUND_ACCOUNTS.get(0)));

    scheduler.fetchCurrentDayTransactions();

    then(eventPublisher)
        .should()
        .publishEvent(new FetchSebCurrentDayTransactionsRequested(SAVINGS_FUND_ACCOUNTS.get(0)));
    then(eventPublisher).shouldHaveNoMoreInteractions();
  }

  @Test
  void fetchEodTransactions_catchesUpEveryMissedDayBeforeYesterdayThroughHistoricStatements() {
    var account = ALL_ACCOUNTS.get(5);
    given(bankAccounts.findAll()).willReturn(List.of(account));
    given(statementCoverage.isReceived(account, YESTERDAY)).willReturn(true);
    given(statementCoverage.missingPeriods(account, CATCH_UP_FROM, DAY_BEFORE_YESTERDAY))
        .willReturn(
            List.of(
                new StatementPeriod(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 8)),
                new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12))));
    var scheduler = scheduler();

    scheduler.fetchEodTransactions();

    then(eventPublisher)
        .should()
        .publishEvent(
            new FetchSebHistoricTransactionsRequested(
                account, LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 8)));
    then(eventPublisher)
        .should()
        .publishEvent(
            new FetchSebHistoricTransactionsRequested(
                account, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12)));
    then(eventPublisher).should(never()).publishEvent(any(FetchSebEodTransactionsRequested.class));
  }

  @Test
  void fetchEodTransactions_continuesWithTheNextGapWhenACatchUpFetchFails() {
    var account = ALL_ACCOUNTS.get(5);
    given(bankAccounts.findAll()).willReturn(List.of(account));
    given(statementCoverage.isReceived(account, YESTERDAY)).willReturn(true);
    var firstGap = new StatementPeriod(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 8));
    var secondGap = new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12));
    given(statementCoverage.missingPeriods(account, CATCH_UP_FROM, DAY_BEFORE_YESTERDAY))
        .willReturn(List.of(firstGap, secondGap));
    var scheduler = scheduler();
    willThrow(new RuntimeException("503 LBR_SERVICE_UNAVAILABLE"))
        .given(eventPublisher)
        .publishEvent(
            new FetchSebHistoricTransactionsRequested(account, firstGap.from(), firstGap.to()));

    scheduler.fetchEodTransactions();

    then(eventPublisher)
        .should()
        .publishEvent(
            new FetchSebHistoricTransactionsRequested(account, secondGap.from(), secondGap.to()));
  }

  @Test
  void
      reportMissingStatements_publishesOneEventListingEveryUnprocessedPeriodOfTheLastWeekIncludingYesterday() {
    var deposit = ALL_ACCOUNTS.get(0);
    var pensionFund = ALL_ACCOUNTS.get(5);
    given(bankAccounts.findAll()).willReturn(List.of(deposit, pensionFund));
    var depositGap = new StatementPeriod(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13));
    var pensionFundGap = new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12));
    given(statementCoverage.unprocessedPeriods(deposit, CATCH_UP_FROM, YESTERDAY))
        .willReturn(List.of(depositGap));
    given(statementCoverage.unprocessedPeriods(pensionFund, CATCH_UP_FROM, YESTERDAY))
        .willReturn(List.of(pensionFundGap));
    var scheduler = scheduler();

    scheduler.reportMissingStatements();

    then(eventPublisher)
        .should()
        .publishEvent(
            new SebStatementGapsFound(
                List.of(
                    new StatementGap(deposit, depositGap),
                    new StatementGap(pensionFund, pensionFundGap))));
  }

  @Test
  void reportMissingStatements_staysQuietWhenEveryStatementOfTheLastWeekIsProcessed() {
    given(bankAccounts.findAll()).willReturn(ALL_ACCOUNTS);
    given(statementCoverage.unprocessedPeriods(any(), eq(CATCH_UP_FROM), eq(YESTERDAY)))
        .willReturn(List.of());
    var scheduler = scheduler();

    scheduler.reportMissingStatements();

    then(eventPublisher).shouldHaveNoInteractions();
  }

  private SebStatementFetchingScheduler scheduler() {
    return new SebStatementFetchingScheduler(
        eventPublisher, bankAccounts, statementCoverage, clock);
  }
}
