package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.*;
import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private BankAccounts bankAccounts;

  @Test
  void fetchCurrentDayTransactions_publishesEventsOnlyForSavingsFundAccounts() {
    when(bankAccounts.findAll(TKF100)).thenReturn(SAVINGS_FUND_ACCOUNTS);
    var scheduler = new SebStatementFetchingScheduler(eventPublisher, bankAccounts);

    scheduler.fetchCurrentDayTransactions();

    var captor = ArgumentCaptor.forClass(FetchSebCurrentDayTransactionsRequested.class);
    verify(eventPublisher, times(SAVINGS_FUND_ACCOUNTS.size())).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(FetchSebCurrentDayTransactionsRequested::account)
        .containsExactlyElementsOf(SAVINGS_FUND_ACCOUNTS);
  }

  @Test
  void fetchCurrentDayTransactions_continuesOnError() {
    when(bankAccounts.findAll(TKF100)).thenReturn(SAVINGS_FUND_ACCOUNTS);
    var scheduler = new SebStatementFetchingScheduler(eventPublisher, bankAccounts);

    doThrow(new RuntimeException("Error"))
        .doNothing()
        .doNothing()
        .when(eventPublisher)
        .publishEvent(any(FetchSebCurrentDayTransactionsRequested.class));

    scheduler.fetchCurrentDayTransactions();

    verify(eventPublisher, times(SAVINGS_FUND_ACCOUNTS.size()))
        .publishEvent(any(FetchSebCurrentDayTransactionsRequested.class));
  }

  @Test
  void fetchEodTransactions_publishesEventsForEveryConfiguredAccountIncludingPensionFunds() {
    when(bankAccounts.findAll()).thenReturn(ALL_ACCOUNTS);
    var scheduler = new SebStatementFetchingScheduler(eventPublisher, bankAccounts);

    scheduler.fetchEodTransactions();

    var captor = ArgumentCaptor.forClass(FetchSebEodTransactionsRequested.class);
    verify(eventPublisher, times(ALL_ACCOUNTS.size())).publishEvent(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(FetchSebEodTransactionsRequested::account)
        .containsExactlyElementsOf(ALL_ACCOUNTS);
  }

  @Test
  void fetchEodTransactions_continuesOnError_andPublishesFailureEvent() {
    when(bankAccounts.findAll()).thenReturn(ALL_ACCOUNTS);
    var scheduler = new SebStatementFetchingScheduler(eventPublisher, bankAccounts);

    doThrow(new RuntimeException("404 LBR_EOD_STATEMENT_NOT_GENERATED"))
        .when(eventPublisher)
        .publishEvent(any(FetchSebEodTransactionsRequested.class));

    scheduler.fetchEodTransactions();

    for (BankAccount account : ALL_ACCOUNTS) {
      verify(eventPublisher)
          .publishEvent(new SebEodFetchFailedEvent(account, "404 LBR_EOD_STATEMENT_NOT_GENERATED"));
    }
  }
}
