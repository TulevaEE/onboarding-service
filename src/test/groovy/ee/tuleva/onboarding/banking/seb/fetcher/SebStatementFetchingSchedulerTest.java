package ee.tuleva.onboarding.banking.seb.fetcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SebStatementFetchingSchedulerTest {

  private ApplicationEventPublisher eventPublisher;
  private SebStatementFetchingScheduler scheduler;

  @BeforeEach
  void setup() {
    eventPublisher = mock(ApplicationEventPublisher.class);
    scheduler = new SebStatementFetchingScheduler(eventPublisher);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void fetchCurrentDayTransactions_publishesEventsForAllAccounts() {
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
  void fetchEodTransactions_continuesOnError() {
    doThrow(new RuntimeException("Error"))
        .doNothing()
        .doNothing()
        .when(eventPublisher)
        .publishEvent(any(FetchSebEodTransactionsRequested.class));

    scheduler.fetchEodTransactions();

    verify(eventPublisher, times(BankAccountType.values().length))
        .publishEvent(any(FetchSebEodTransactionsRequested.class));
  }

  @Test
  void fetchLast7DaysTransactions_publishesEventsWithCorrectDateRange() {
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate sevenDaysAgo = LocalDate.of(2025, 1, 8);
    ClockHolder.setClock(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    scheduler.fetchLast7DaysTransactions();

    var captor = ArgumentCaptor.forClass(FetchSebHistoricTransactionsRequested.class);
    verify(eventPublisher, times(BankAccountType.values().length)).publishEvent(captor.capture());

    List<FetchSebHistoricTransactionsRequested> events = captor.getAllValues();
    assertThat(events).hasSize(BankAccountType.values().length);

    for (BankAccountType accountType : BankAccountType.values()) {
      assertThat(events)
          .anyMatch(
              event ->
                  event.accountType() == accountType
                      && event.dateFrom().equals(sevenDaysAgo)
                      && event.dateTo().equals(today));
    }
  }

  @Test
  void fetchLast7DaysTransactions_continuesOnError() {
    LocalDate today = LocalDate.of(2025, 1, 15);
    ClockHolder.setClock(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    doThrow(new RuntimeException("Error"))
        .doNothing()
        .doNothing()
        .when(eventPublisher)
        .publishEvent(any(FetchSebHistoricTransactionsRequested.class));

    scheduler.fetchLast7DaysTransactions();

    verify(eventPublisher, times(BankAccountType.values().length))
        .publishEvent(any(FetchSebHistoricTransactionsRequested.class));
  }
}
