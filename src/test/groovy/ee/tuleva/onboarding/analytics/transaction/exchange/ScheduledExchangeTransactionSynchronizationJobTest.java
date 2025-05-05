package ee.tuleva.onboarding.analytics.transaction.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledExchangeTransactionSynchronizationJobTest {

  private static final ZoneId TALLINN_ZONE = ZoneId.of("Europe/Tallinn");

  @Mock private ExchangeTransactionSynchronizer exchangeTransactionSynchronizer;
  @Mock private MandateDeadlinesService mandateDeadlinesService;
  @Mock private Clock clock;

  @InjectMocks private ScheduledExchangeTransactionSynchronizationJob job;

  @Captor private ArgumentCaptor<LocalDate> syncStartDateCaptor;
  @Captor private ArgumentCaptor<Instant> getDeadlinesInstantCaptor;

  private final LocalDate jobInternalHistoricalSyncEndDate = LocalDate.of(2024, 11, 30);

  private Instant startOfDayInstant(LocalDate date) {
    return date.atStartOfDay(TALLINN_ZONE).toInstant();
  }

  private Instant endOfDayInstant(LocalDate date) {
    return date.atTime(23, 59, 59, 999_999_999).atZone(TALLINN_ZONE).toInstant();
  }

  @BeforeEach
  void setup() {
    lenient().when(clock.getZone()).thenReturn(TALLINN_ZONE);
  }

  @Nested
  @DisplayName("run() - Scheduled Daily Sync")
  class RunTest {
    @Test
    @DisplayName("calls synchronizer with start date from MandateDeadlinesService")
    void run_callsSynchronizerWithStartDateFromMandateService() {
      // given
      LocalDate expectedStartDate = LocalDate.of(2025, 5, 1);
      when(mandateDeadlinesService.getCurrentPeriodStartDate()).thenReturn(expectedStartDate);

      // when
      job.run();

      // then
      verify(mandateDeadlinesService).getCurrentPeriodStartDate();
      verify(exchangeTransactionSynchronizer)
          .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verifyNoMoreInteractions(exchangeTransactionSynchronizer);
      verify(mandateDeadlinesService, times(1)).getCurrentPeriodStartDate();
      verifyNoMoreInteractions(mandateDeadlinesService);
    }
  }

  @Nested
  @DisplayName("runInitialTransactionsSync() - One-off Sync")
  class RunInitialSyncTest {
    @Test
    @DisplayName("calls synchronizer with fixed start date and no mandate service interaction")
    void runInitialTransactionsSync_callsSynchronizerWithFixedStartDate() {
      // given
      LocalDate expectedStartDate = LocalDate.of(2024, 12, 1);

      // when
      job.runInitialTransactionsSync();

      // then
      verify(exchangeTransactionSynchronizer)
          .sync(eq(expectedStartDate), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verifyNoInteractions(mandateDeadlinesService);
      verifyNoMoreInteractions(exchangeTransactionSynchronizer);
    }
  }

  @Nested
  @DisplayName("synchronizeHistoricalData() - Historical Sync")
  class SynchronizeHistoricalDataTest {

    LocalDate p1_Start = LocalDate.of(2016, 12, 1);
    LocalDate p1_End = LocalDate.of(2017, 3, 31);
    LocalDate p2_Start = LocalDate.of(2017, 4, 1);
    LocalDate p2_End = LocalDate.of(2017, 7, 31);
    LocalDate p3_Start = LocalDate.of(2017, 8, 1);
    LocalDate p4_End_ForTermination = jobInternalHistoricalSyncEndDate.plusDays(1);

    private MandateDeadlines mockDeadlines(
        LocalDate currentPeriodStart, LocalDate currentPeriodEnd) {
      MandateDeadlines deadlines = mock(MandateDeadlines.class);
      lenient().when(deadlines.getCurrentPeriodStartDate()).thenReturn(currentPeriodStart);
      lenient().when(deadlines.getPeriodEnding()).thenReturn(endOfDayInstant(currentPeriodEnd));
      return deadlines;
    }

    @Test
    @DisplayName("iterates through first few periods and calls sync, then stops")
    void synchronizeHistoricalData_callsSyncForFirstFewPeriodsAndStops() {
      Instant firstInstant = startOfDayInstant(LocalDate.of(2017, 3, 28));
      MandateDeadlines initialDeadlines = mockDeadlines(p1_Start, p1_End);
      when(mandateDeadlinesService.getDeadlines(eq(firstInstant))).thenReturn(initialDeadlines);

      MandateDeadlines deadlines_p1 = mockDeadlines(p1_Start, p1_End);
      when(mandateDeadlinesService.getDeadlines(eq(startOfDayInstant(p1_Start))))
          .thenReturn(deadlines_p1);

      MandateDeadlines deadlines_p2 = mockDeadlines(p2_Start, p2_End);
      when(mandateDeadlinesService.getDeadlines(eq(startOfDayInstant(p2_Start))))
          .thenReturn(deadlines_p2);

      MandateDeadlines deadlines_p3_leading_to_termination = mock(MandateDeadlines.class);
      when(deadlines_p3_leading_to_termination.getPeriodEnding())
          .thenReturn(endOfDayInstant(p4_End_ForTermination));
      when(mandateDeadlinesService.getDeadlines(eq(startOfDayInstant(p3_Start))))
          .thenReturn(deadlines_p3_leading_to_termination);

      doNothing()
          .when(exchangeTransactionSynchronizer)
          .sync(eq(p1_Start), any(), any(), anyBoolean());
      doNothing()
          .when(exchangeTransactionSynchronizer)
          .sync(eq(p2_Start), any(), any(), anyBoolean());
      doNothing()
          .when(exchangeTransactionSynchronizer)
          .sync(eq(p3_Start), any(), any(), anyBoolean());

      // Act
      job.synchronizeHistoricalData();

      // Assert
      int expectedSyncCalls = 3; // p1, p2, p3

      // Verify exactly 3 calls happened
      verify(exchangeTransactionSynchronizer, times(expectedSyncCalls))
          .sync(
              syncStartDateCaptor.capture(), eq(Optional.empty()), eq(Optional.empty()), eq(false));

      List<LocalDate> actualSyncDates = syncStartDateCaptor.getAllValues();
      assertThat(actualSyncDates).containsExactly(p1_Start, p2_Start, p3_Start);

      verify(mandateDeadlinesService, times(expectedSyncCalls + 1))
          .getDeadlines(getDeadlinesInstantCaptor.capture());

      List<Instant> actualDeadlineInstants = getDeadlinesInstantCaptor.getAllValues();
      assertThat(actualDeadlineInstants)
          .containsExactly(
              firstInstant, // Initial call
              startOfDayInstant(p1_Start), // Loop 1 iter (calculates p2_Start)
              startOfDayInstant(p2_Start), // Loop 2 iter (calculates p3_Start)
              startOfDayInstant(p3_Start) // Loop 3 iter (calculates termination date)
              );

      verify(initialDeadlines, times(1)).getCurrentPeriodStartDate();
      verify(deadlines_p1, times(1)).getPeriodEnding(); // Loop 1
      verify(deadlines_p2, times(1)).getPeriodEnding(); // Loop 2
      verify(deadlines_p3_leading_to_termination, times(1)).getPeriodEnding(); // Loop 3
    }

    @Test
    @DisplayName("stops processing and logs error if sync fails")
    void synchronizeHistoricalData_stopsOnError() {
      // Arrange
      LocalDate failPeriodStart = p2_Start; // Sync for p2 will fail

      Instant firstInstant = startOfDayInstant(LocalDate.of(2017, 3, 28));
      MandateDeadlines initialDeadlines = mockDeadlines(p1_Start, p1_End);
      when(mandateDeadlinesService.getDeadlines(eq(firstInstant))).thenReturn(initialDeadlines);

      MandateDeadlines deadlines_p1 = mockDeadlines(p1_Start, p1_End);
      when(mandateDeadlinesService.getDeadlines(eq(startOfDayInstant(p1_Start))))
          .thenReturn(deadlines_p1);

      doNothing()
          .when(exchangeTransactionSynchronizer)
          .sync(eq(p1_Start), any(), any(), anyBoolean()); // p1 succeeds
      doThrow(new RuntimeException("Sync failed!"))
          .when(exchangeTransactionSynchronizer)
          .sync(eq(failPeriodStart), any(), any(), anyBoolean()); // p2 fails

      // Act
      job.synchronizeHistoricalData();

      // Assert
      verify(exchangeTransactionSynchronizer, times(1))
          .sync(eq(p1_Start), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verify(exchangeTransactionSynchronizer, times(1))
          .sync(eq(failPeriodStart), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verify(exchangeTransactionSynchronizer, never())
          .sync(eq(p3_Start), any(), any(), anyBoolean());

      verify(mandateDeadlinesService, times(2)).getDeadlines(getDeadlinesInstantCaptor.capture());
      List<Instant> capturedInstants = getDeadlinesInstantCaptor.getAllValues();
      assertThat(capturedInstants).containsExactly(firstInstant, startOfDayInstant(p1_Start));
    }

    @Test
    @DisplayName("stops processing and logs error if date calculation fails to advance")
    void synchronizeHistoricalData_stopsIfDateDoesNotAdvance() {
      // Arrange
      LocalDate stuckPeriodStart = p2_Start;

      Instant firstInstant = startOfDayInstant(LocalDate.of(2017, 3, 28));
      MandateDeadlines initialDeadlines = mockDeadlines(p1_Start, p1_End);
      when(mandateDeadlinesService.getDeadlines(eq(firstInstant))).thenReturn(initialDeadlines);

      MandateDeadlines deadlines_p1 = mockDeadlines(p1_Start, p1_End);
      when(mandateDeadlinesService.getDeadlines(eq(startOfDayInstant(p1_Start))))
          .thenReturn(deadlines_p1);

      MandateDeadlines deadlines_p2_stuck = mock(MandateDeadlines.class);
      lenient().when(deadlines_p2_stuck.getCurrentPeriodStartDate()).thenReturn(stuckPeriodStart);
      when(deadlines_p2_stuck.getPeriodEnding()).thenReturn(endOfDayInstant(p1_End));
      when(mandateDeadlinesService.getDeadlines(eq(startOfDayInstant(stuckPeriodStart))))
          .thenReturn(deadlines_p2_stuck);

      doNothing()
          .when(exchangeTransactionSynchronizer)
          .sync(eq(p1_Start), any(), any(), anyBoolean());
      doNothing()
          .when(exchangeTransactionSynchronizer)
          .sync(eq(stuckPeriodStart), any(), any(), anyBoolean());

      // Act
      job.synchronizeHistoricalData();

      // Assert
      verify(exchangeTransactionSynchronizer, times(1))
          .sync(eq(p1_Start), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verify(exchangeTransactionSynchronizer, times(1))
          .sync(eq(stuckPeriodStart), eq(Optional.empty()), eq(Optional.empty()), eq(false));
      verify(exchangeTransactionSynchronizer, never())
          .sync(eq(p3_Start), any(), any(), anyBoolean());

      verify(mandateDeadlinesService, times(3)).getDeadlines(getDeadlinesInstantCaptor.capture());
      List<Instant> capturedInstants = getDeadlinesInstantCaptor.getAllValues();
      assertThat(capturedInstants)
          .containsExactly(firstInstant, startOfDayInstant(p1_Start), startOfDayInstant(p2_Start));

      verify(deadlines_p2_stuck, times(1)).getPeriodEnding();
    }
  }
}
