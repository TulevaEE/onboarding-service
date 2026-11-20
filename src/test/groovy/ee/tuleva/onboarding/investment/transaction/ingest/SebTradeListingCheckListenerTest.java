package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SebTradeListingCheckListenerTest {

  private static final Instant MORNING = Instant.parse("2026-02-13T08:15:00Z");
  private static final ReportImportCompleted PENDING_IMPORT =
      new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 2, 13), 3);

  private final SebTradeListingCheck check = mock(SebTradeListingCheck.class);
  private final MutableClock clock = new MutableClock(MORNING);

  @Test
  void runsAfterTheSebPendingImport() {
    listener(RecordingLockProvider.free()).onReportImportCompleted(PENDING_IMPORT);

    then(check).should().run();
  }

  @Test
  void ignoresOtherReportImports() {
    listener(RecordingLockProvider.free())
        .onReportImportCompleted(
            new ReportImportCompleted(SEB, POSITIONS, LocalDate.of(2026, 2, 13), 9));

    then(check).shouldHaveNoInteractions();
  }

  @Test
  void skipsWhileTheReconciliationLockIsHeldElsewhere_theNextTriggerCatchesUp() {
    listener(RecordingLockProvider.heldElsewhere()).onReportImportCompleted(PENDING_IMPORT);

    then(check).shouldHaveNoInteractions();
  }

  @Test
  void runsAfterBankMessagesAreProcessed_atMostOnceInThirtyMinutes() {
    SebTradeListingCheckListener listener = listener(RecordingLockProvider.free());

    listener.onBankMessagesProcessed();
    clock.advance(Duration.ofMinutes(29));
    listener.onBankMessagesProcessed();
    clock.advance(Duration.ofMinutes(1));
    listener.onBankMessagesProcessed();

    then(check).should(times(2)).run();
  }

  @Test
  void aManualTriggerRunsTheCheck() {
    listener(RecordingLockProvider.free()).onSebTradeListingCheckRequested();

    then(check).should().run();
  }

  @Test
  void aManualTriggerWhileTheLockIsHeld_failsSoThePollerRetriesIt() {
    assertThatThrownBy(
            () -> listener(RecordingLockProvider.heldElsewhere()).onSebTradeListingCheckRequested())
        .isInstanceOf(SebReconciliationAlreadyRunningException.class);
    then(check).shouldHaveNoInteractions();
  }

  @Test
  void aCheckThatThrows_doesNotFailTheImport() {
    willThrow(new IllegalStateException("database down")).given(check).run();

    listener(RecordingLockProvider.free()).onReportImportCompleted(PENDING_IMPORT);

    then(check).should().run();
  }

  private SebTradeListingCheckListener listener(RecordingLockProvider lockProvider) {
    return new SebTradeListingCheckListener(
        clock, check, lockProvider.reconciliationLockAt(Clock.fixed(MORNING, ZoneId.of("UTC"))));
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Europe/Tallinn");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
