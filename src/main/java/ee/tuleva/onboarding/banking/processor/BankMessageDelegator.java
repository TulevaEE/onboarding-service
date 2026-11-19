package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.STATEMENT_UNPROCESSABLE;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.time.Clock;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankMessageDelegator {
  private final Clock clock;
  private final BankingMessageRepository bankingMessageRepository;
  private final PaymentCheckService paymentCheckService;
  private final BankStatementExtractor bankStatementExtractor;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener(ProcessBankMessagesRequested.class)
  public void onProcessRequested() {
    log.info("Processing bank messages");
    var messages =
        bankingMessageRepository.findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtAsc();

    for (BankingMessage message : messages) {
      processMessage(message);
    }
    log.info("Finished processing bank messages: processedMessages={}", messages.size());
    eventPublisher.publishEvent(new BankMessagesProcessingCompleted());
  }

  private void processMessage(BankingMessage message) {
    try {
      var messageType = BankMessageType.ofMessage(message.getRawResponse());
      message.setMessageType(messageType);

      var bankStatement =
          extractBankStatement(message.getRawResponse(), messageType, message.getTimezoneId());
      message.setAccountIban(bankStatement.getBankStatementAccount().iban());
      message.setStatementFrom(bankStatement.getPeriod().from());
      message.setStatementTo(bankStatement.getPeriod().to());
      var statementEvent =
          new BankStatementReceived(message.getId(), message.getBankType(), bankStatement);
      eventPublisher.publishEvent(statementEvent);

      message.setProcessedAt(clock.instant());
      bankingMessageRepository.save(message);
    } catch (Exception e) {
      log.error("Failed to process message: messageId={}", message.getId(), e);
      paymentCheckService.record(
          STATEMENT_UNPROCESSABLE,
          HOLD,
          String.valueOf(message.getId()),
          "a bank message could not be processed: " + e.getClass().getSimpleName());
      message.setFailedAt(clock.instant());
      bankingMessageRepository.save(message);
    }
  }

  private BankStatement extractBankStatement(
      String rawResponse, BankMessageType messageType, ZoneId timezone) {
    return switch (messageType) {
      case INTRA_DAY_REPORT ->
          bankStatementExtractor.extractFromIntraDayReport(rawResponse, timezone);
      case HISTORIC_STATEMENT ->
          bankStatementExtractor.extractFromHistoricStatement(rawResponse, timezone);
    };
  }
}
