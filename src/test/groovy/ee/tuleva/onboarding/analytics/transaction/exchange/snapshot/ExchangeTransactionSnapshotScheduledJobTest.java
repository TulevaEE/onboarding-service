package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeTransactionSnapshotScheduledJobTest {

  @Mock private ExchangeTransactionSnapshotService exchangeTransactionSnapshotService;

  @InjectMocks private ExchangeTransactionSnapshotScheduledJob scheduledJob;

  @Test
  void takeWeeklySnapshot_callsServiceWithCorrectJobType() {
    // when
    scheduledJob.takeWeeklySnapshot();

    // then
    verify(exchangeTransactionSnapshotService).takeSnapshot("WEEKLY");
  }

  @Test
  void takeMonthlySnapshot_callsServiceWithCorrectJobType() {
    // when
    scheduledJob.takeMonthlySnapshot();

    // then
    verify(exchangeTransactionSnapshotService).takeSnapshot("MONTHLY");
  }

  @Test
  void temporaryMissingPeriodSnapshot_callsServiceWithSpecificDateAndJobType() {
    // given
    LocalDateTime expectedDateTime = LocalDateTime.of(2025, 8, 1, 0, 1);
    ZoneId estonianZone = ZoneId.of("Europe/Tallinn");
    OffsetDateTime expectedSnapshotTime = expectedDateTime.atZone(estonianZone).toOffsetDateTime();
    LocalDate expectedReportingDate = LocalDate.of(2025, 4, 1);

    // when
    scheduledJob.temporaryMissingPeriodSnapshot();

    // then
    verify(exchangeTransactionSnapshotService)
        .takeSnapshotForReportingDate(
            eq("PERIOD_FIX"), eq(expectedSnapshotTime), eq(expectedReportingDate));
  }
}
