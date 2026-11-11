package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ApplicationListenerMethodAdapter;

@ExtendWith(MockitoExtension.class)
class SettlementFromBankListenerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneId.of("Europe/Tallinn"));
  private static final LocalDate SIXTY_DAYS_AGO = LocalDate.of(2026, 9, 4);
  private static final ReportImportCompleted SEB_PENDING_IMPORTED =
      new ReportImportCompleted(SEB, PENDING_TRANSACTIONS, LocalDate.of(2026, 11, 3), 1);

  @Mock private SettlementFromBankService settlementFromBankService;
  @Mock private JobTriggerQueue jobTriggerQueue;

  private SettlementFromBankListener listenerLockedBy(RecordingLockProvider lockProvider) {
    return new SettlementFromBankListener(
        CLOCK,
        settlementFromBankService,
        lockProvider.reconciliationLockAt(CLOCK),
        jobTriggerQueue);
  }

  @Test
  void afterAReportImport_reconciliationRunsBeforeTheBankSettles() throws Exception {
    var reconciliation =
        new ApplicationListenerMethodAdapter(
            "reconciliation",
            SebPendingTransactionReconciliationListener.class,
            SebPendingTransactionReconciliationListener.class.getMethod(
                "onReportImportCompleted", ReportImportCompleted.class));
    var bankSettlement =
        new ApplicationListenerMethodAdapter(
            "bankSettlement",
            SettlementFromBankListener.class,
            SettlementFromBankListener.class.getMethod(
                "onReportImportCompleted", ReportImportCompleted.class));

    assertThat(reconciliation.getOrder()).isLessThan(bankSettlement.getOrder());
  }

  @Test
  void sebPendingReportImported_settlesTheOrdersOfTheLookbackUnderTheReconciliationLock() {
    RecordingLockProvider lockProvider = RecordingLockProvider.free();
    List<Boolean> lockHeldWhileSettling = new ArrayList<>();
    willAnswer(
            invocation -> {
              lockHeldWhileSettling.add(lockProvider.isHeld());
              return Optional.empty();
            })
        .given(settlementFromBankService)
        .settleOrdersBookedSince(eq(SIXTY_DAYS_AGO), any());

    listenerLockedBy(lockProvider).onReportImportCompleted(SEB_PENDING_IMPORTED);

    assertThat(lockHeldWhileSettling).containsExactly(true);
    assertThat(lockProvider.lockNames()).containsExactly(SebReconciliationLock.NAME);
    then(jobTriggerQueue).shouldHaveNoInteractions();
  }

  @Test
  void sebPendingReportImportedWhileTheLockIsHeld_settlesNothingAndDefersTheBankToItsTrigger() {
    listenerLockedBy(RecordingLockProvider.heldElsewhere())
        .onReportImportCompleted(SEB_PENDING_IMPORTED);

    then(settlementFromBankService).shouldHaveNoInteractions();
    then(jobTriggerQueue).should().defer("SettlementFromBankJob");
  }

  @Test
  void aSettlementThatLosesTheLock_isDeferredToItsTriggerWithoutFailingTheImport() {
    willThrow(new SettlementFromBankLockLostException(42L))
        .given(settlementFromBankService)
        .settleOrdersBookedSince(eq(SIXTY_DAYS_AGO), any());
    SettlementFromBankListener listener = listenerLockedBy(RecordingLockProvider.free());

    assertThatCode(() -> listener.onReportImportCompleted(SEB_PENDING_IMPORTED))
        .doesNotThrowAnyException();

    then(jobTriggerQueue).should().defer("SettlementFromBankJob");
  }

  @Test
  void theDeferredTriggerOnceTheLockIsFree_settlesTheOrdersOfTheLookback() {
    listenerLockedBy(RecordingLockProvider.free()).onSettlementFromBankRequested();

    then(settlementFromBankService).should().settleOrdersBookedSince(eq(SIXTY_DAYS_AGO), any());
  }

  @Test
  void theDeferredTriggerWhileTheLockIsStillHeld_waitsAsBusy() {
    SettlementFromBankListener stillLockedOut =
        listenerLockedBy(RecordingLockProvider.heldElsewhere());

    assertThatThrownBy(stillLockedOut::onSettlementFromBankRequested)
        .isInstanceOf(JobBusyException.class);

    then(settlementFromBankService).shouldHaveNoInteractions();
  }

  @Test
  void theDeferredTriggerThatFails_failsItsTrigger() {
    willThrow(new IllegalStateException("boom"))
        .given(settlementFromBankService)
        .settleOrdersBookedSince(any(), any());
    SettlementFromBankListener listener = listenerLockedBy(RecordingLockProvider.free());

    assertThatThrownBy(listener::onSettlementFromBankRequested)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void otherReportsImported_settleNothing() {
    SettlementFromBankListener listener = listenerLockedBy(RecordingLockProvider.free());

    listener.onReportImportCompleted(
        new ReportImportCompleted(SEB, POSITIONS, LocalDate.of(2026, 11, 3), 1));
    listener.onReportImportCompleted(
        new ReportImportCompleted(SWEDBANK, PENDING_TRANSACTIONS, LocalDate.of(2026, 11, 3), 1));

    then(settlementFromBankService).shouldHaveNoInteractions();
    then(jobTriggerQueue).shouldHaveNoInteractions();
  }

  @Test
  void aFailureToSettle_doesNotFailTheReportImport() {
    willThrow(new IllegalStateException("boom"))
        .given(settlementFromBankService)
        .settleOrdersBookedSince(any(), any());
    SettlementFromBankListener listener = listenerLockedBy(RecordingLockProvider.free());

    assertThatCode(() -> listener.onReportImportCompleted(SEB_PENDING_IMPORTED))
        .doesNotThrowAnyException();
    then(jobTriggerQueue).shouldHaveNoInteractions();
  }
}
