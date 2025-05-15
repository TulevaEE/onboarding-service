package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import static org.mockito.Mockito.verify;

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
}
