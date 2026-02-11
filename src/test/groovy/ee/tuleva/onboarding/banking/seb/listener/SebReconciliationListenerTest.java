package ee.tuleva.onboarding.banking.seb.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.seb.reconciliation.SebReconciliator;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith(MockitoExtension.class)
class SebReconciliationListenerTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T10:00:00Z");
  private static final Duration RECONCILIATION_DELAY = Duration.ofMinutes(5);

  @Mock private SebReconciliator reconciliator;
  @Mock private TaskScheduler taskScheduler;

  private SebReconciliationListener listener;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
    listener =
        new SebReconciliationListener(
            reconciliator, taskScheduler, fixedClock, RECONCILIATION_DELAY);
  }

  @Test
  void reconcile_shouldScheduleReconciliationWithDelay() {
    var statement = createStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);
    Instant expectedScheduledTime = FIXED_INSTANT.plus(RECONCILIATION_DELAY);

    listener.reconcile(event);

    var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).schedule(runnableCaptor.capture(), eq(expectedScheduledTime));

    runnableCaptor.getValue().run();
    verify(reconciliator).reconcile(statement);
  }

  @Test
  void reconcile_shouldIgnoreNonSebStatements() {
    var statement = createStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SWEDBANK, statement);

    listener.reconcile(event);

    verifyNoInteractions(taskScheduler);
    verifyNoInteractions(reconciliator);
  }

  @Test
  void reconcile_shouldIgnoreNonHistoricStatements() {
    var statement = createStatement(INTRA_DAY_REPORT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);

    listener.reconcile(event);

    verifyNoInteractions(taskScheduler);
    verifyNoInteractions(reconciliator);
  }

  @Test
  void reconcile_shouldCatchExceptionsInScheduledTask() {
    var statement = createStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);
    doThrow(new IllegalStateException("Reconciliation failed"))
        .when(reconciliator)
        .reconcile(statement);

    listener.reconcile(event);

    var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(taskScheduler).schedule(runnableCaptor.capture(), any(Instant.class));

    runnableCaptor.getValue().run();
    verify(reconciliator).reconcile(statement);
  }

  private BankStatement createStatement(BankStatementType type) {
    return new BankStatement(
        type,
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678"),
        List.of(),
        List.of());
  }
}
