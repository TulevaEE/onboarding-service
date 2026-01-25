package ee.tuleva.onboarding.banking.seb.listener;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.seb.processor.SebBankStatementProcessor;
import ee.tuleva.onboarding.banking.statement.BankStatement;
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
class SebBankStatementListenerTest {

  @Mock private SebBankStatementProcessor processor;

  @InjectMocks private SebBankStatementListener listener;

  @Test
  void processStatement_shouldProcessSebStatements() {
    var statement = createStatement();
    var event = new BankStatementReceived(UUID.randomUUID(), SEB, statement);

    listener.processStatement(event);

    verify(processor).processStatement(statement);
  }

  @Test
  void processStatement_shouldIgnoreNonSebStatements() {
    var statement = createStatement();
    var event = new BankStatementReceived(UUID.randomUUID(), SWEDBANK, statement);

    listener.processStatement(event);

    verifyNoInteractions(processor);
  }

  private BankStatement createStatement() {
    return new BankStatement(
        INTRA_DAY_REPORT,
        new BankStatementAccount("EE123456789012345678", "Test Company", "12345678"),
        List.of(),
        List.of(),
        Instant.now());
  }
}
