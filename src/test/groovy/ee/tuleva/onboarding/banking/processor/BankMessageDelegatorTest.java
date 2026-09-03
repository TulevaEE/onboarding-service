package ee.tuleva.onboarding.banking.processor;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BankMessageDelegatorTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Mock private BankingMessageRepository bankingMessageRepository;
  @Mock private BankStatementExtractor bankStatementExtractor;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Test
  void onProcessRequested_publishesCompletionEventAfterProcessingAllMessages() {
    given(
            bankingMessageRepository
                .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .willReturn(List.of());
    var delegator =
        new BankMessageDelegator(
            clock, bankingMessageRepository, bankStatementExtractor, eventPublisher);

    delegator.onProcessRequested(new ProcessBankMessagesRequested());

    then(eventPublisher).should().publishEvent(new BankMessagesProcessingCompleted());
  }
}
