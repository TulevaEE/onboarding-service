package ee.tuleva.onboarding.banking.seb.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.seb.reconciliation.SebReconciliator;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebReconciliationListenerTest {

  @Mock private SebReconciliator reconciliator;

  @InjectMocks private SebReconciliationListener listener;

  @Test
  void reconcile_shouldReconcileHistoricSebStatements() {
    var statement = createStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);

    listener.reconcile(event);

    verify(reconciliator).reconcile(statement);
  }

  @Test
  void reconcile_shouldIgnoreNonSebStatements() {
    var statement = createStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SWEDBANK, statement);

    listener.reconcile(event);

    verifyNoInteractions(reconciliator);
  }

  @Test
  void reconcile_shouldIgnoreNonHistoricStatements() {
    var statement = createStatement(INTRA_DAY_REPORT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);

    listener.reconcile(event);

    verifyNoInteractions(reconciliator);
  }

  @Test
  void reconcile_shouldCatchExceptionsAndLogError() {
    var statement = createStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);
    doThrow(new IllegalStateException("Reconciliation failed"))
        .when(reconciliator)
        .reconcile(statement);

    listener.reconcile(event);

    verify(reconciliator).reconcile(statement);
  }

  private BankStatement createStatement(BankStatementType type) {
    return new BankStatement(
        type,
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678"),
        List.of(),
        List.of(),
        Instant.now());
  }
}
