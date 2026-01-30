package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankMessageProcessingScheduler {

  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "BankMessageProcessingScheduler_processMessages",
      lockAtMostFor = "50s",
      lockAtLeastFor = "5s")
  public void processMessages() {
    log.info("Running bank message processing scheduler");
    eventPublisher.publishEvent(new ProcessBankMessagesRequested());
  }
}
