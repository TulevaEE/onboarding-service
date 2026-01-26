package ee.tuleva.onboarding.banking.processor;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BankMessageProcessingSchedulerTest {

  private ApplicationEventPublisher eventPublisher;
  private BankMessageProcessingScheduler scheduler;

  @BeforeEach
  void setUp() {
    eventPublisher = mock(ApplicationEventPublisher.class);
    scheduler = new BankMessageProcessingScheduler(eventPublisher);
  }

  @Test
  void processMessages_publishesProcessBankMessagesRequestedEvent() {
    scheduler.processMessages();

    verify(eventPublisher).publishEvent(any(ProcessBankMessagesRequested.class));
  }
}
