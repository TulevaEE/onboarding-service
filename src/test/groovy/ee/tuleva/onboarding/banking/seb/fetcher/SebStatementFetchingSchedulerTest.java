package ee.tuleva.onboarding.banking.seb.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccountType;
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

  @Mock private ApplicationEventPublisher eventPublisher;

  @Test
  void fetchCurrentDayTransactions_publishesEventsForAllAccounts() {
    var scheduler = new SebStatementFetchingScheduler(eventPublisher);

    scheduler.fetchCurrentDayTransactions();

    var captor = ArgumentCaptor.forClass(FetchSebCurrentDayTransactionsRequested.class);
    verify(eventPublisher, times(BankAccountType.values().length)).publishEvent(captor.capture());

    List<FetchSebCurrentDayTransactionsRequested> events = captor.getAllValues();
    assertThat(events).hasSize(BankAccountType.values().length);

    for (BankAccountType accountType : BankAccountType.values()) {
      assertThat(events).anyMatch(event -> event.accountType() == accountType);
    }
  }

  @Test
  void fetchCurrentDayTransactions_continuesOnError() {
    var scheduler = new SebStatementFetchingScheduler(eventPublisher);

    doThrow(new RuntimeException("Error"))
        .doNothing()
        .doNothing()
        .when(eventPublisher)
        .publishEvent(any(FetchSebCurrentDayTransactionsRequested.class));

    scheduler.fetchCurrentDayTransactions();

    verify(eventPublisher, times(BankAccountType.values().length))
        .publishEvent(any(FetchSebCurrentDayTransactionsRequested.class));
  }

  @Test
  void fetchEodTransactions_publishesEventsForAllAccounts() {
    var scheduler = new SebStatementFetchingScheduler(eventPublisher);

    scheduler.fetchEodTransactions();

    var captor = ArgumentCaptor.forClass(FetchSebEodTransactionsRequested.class);
    verify(eventPublisher, times(BankAccountType.values().length)).publishEvent(captor.capture());

    List<FetchSebEodTransactionsRequested> events = captor.getAllValues();
    assertThat(events).hasSize(BankAccountType.values().length);

    for (BankAccountType accountType : BankAccountType.values()) {
      assertThat(events).anyMatch(event -> event.accountType() == accountType);
    }
  }

  @Test
  void fetchEodTransactions_continuesOnError_andPublishesFailureEvent() {
    var scheduler = new SebStatementFetchingScheduler(eventPublisher);

    doThrow(new RuntimeException("404 LBR_EOD_STATEMENT_NOT_GENERATED"))
        .when(eventPublisher)
        .publishEvent(any(FetchSebEodTransactionsRequested.class));

    scheduler.fetchEodTransactions();

    for (BankAccountType account : BankAccountType.values()) {
      verify(eventPublisher)
          .publishEvent(new SebEodFetchFailedEvent(account, "404 LBR_EOD_STATEMENT_NOT_GENERATED"));
    }
  }
}
