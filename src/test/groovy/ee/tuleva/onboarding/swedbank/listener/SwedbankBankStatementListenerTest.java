package ee.tuleva.onboarding.swedbank.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.swedbank.processor.SwedbankBankStatementProcessor;
import ee.tuleva.onboarding.swedbank.reconcillation.Reconciliator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwedbankBankStatementListenerTest {

  @Mock SwedbankBankStatementProcessor processor;
  @Mock Reconciliator reconciliator;

  @InjectMocks SwedbankBankStatementListener listener;

  @Test
  @DisplayName("Processes statement and reconciles for SWEDBANK historic statement")
  void processesAndReconcilesForSwedbankHistoricStatement() {
    var statement = createBankStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SWEDBANK, statement);

    listener.onBankStatementReceived(event);

    verify(processor).processStatement(statement);
    verify(reconciliator).reconcile(statement);
  }

  @Test
  @DisplayName("Processes statement without reconciliation for SWEDBANK intra-day report")
  void processesWithoutReconciliationForSwedbankIntraDayReport() {
    var statement = createBankStatement(INTRA_DAY_REPORT);
    var event = new BankStatementReceived(UUID.randomUUID(), SWEDBANK, statement);

    listener.onBankStatementReceived(event);

    verify(processor).processStatement(statement);
    verify(reconciliator, never()).reconcile(any());
  }

  @Test
  @DisplayName("Skips processing for non-SWEDBANK bank type")
  void skipsProcessingForNonSwedbankBankType() {
    var statement = createBankStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);

    listener.onBankStatementReceived(event);

    verifyNoInteractions(processor);
    verifyNoInteractions(reconciliator);
  }

  @Test
  @DisplayName("Catches and logs reconciliation errors")
  void catchesReconciliationErrors() {
    var statement = createBankStatement(HISTORIC_STATEMENT);
    var event = new BankStatementReceived(UUID.randomUUID(), SWEDBANK, statement);
    doThrow(new RuntimeException("Reconciliation failed")).when(reconciliator).reconcile(statement);

    listener.onBankStatementReceived(event);

    verify(processor).processStatement(statement);
    verify(reconciliator).reconcile(statement);
  }

  private BankStatement createBankStatement(BankStatement.BankStatementType type) {
    return new BankStatement(
        type,
        new BankStatementAccount("EE442200221092874625", "Tuleva Fondid AS", "14118923"),
        List.of(),
        List.of(),
        Instant.now());
  }
}
