package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.message.BankMessageType.PAYMENT_ORDER_CONFIRMATION;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import java.io.StringReader;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
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
  private final BankStatementExtractor bankStatementExtractor;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void onProcessRequested(ProcessBankMessagesRequested event) {
    log.info("Processing bank messages");
    var messages =
        bankingMessageRepository.findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc();

    for (BankingMessage message : messages) {
      processMessage(message);
    }
    log.info("Finished processing bank messages: processedMessages={}", messages.size());
    eventPublisher.publishEvent(new BankMessagesProcessingCompleted());
  }

  private void processMessage(BankingMessage message) {
    try {
      var messageName =
          extractMessageName(extractNamespace(message.getRawResponse()).orElseThrow());
      var messageType = BankMessageType.fromXmlType(messageName);

      if (messageType == PAYMENT_ORDER_CONFIRMATION) {
        log.info(
            "Payment order confirmation received: messageId={}, bankType={}",
            message.getId(),
            message.getBankType());
      } else {
        var bankStatement =
            extractBankStatement(message.getRawResponse(), messageType, message.getTimezoneId());
        var statementEvent =
            new BankStatementReceived(message.getId(), message.getBankType(), bankStatement);
        eventPublisher.publishEvent(statementEvent);
      }

      message.setProcessedAt(clock.instant());
      bankingMessageRepository.save(message);
    } catch (Exception e) {
      log.error("Failed to process message: messageId={}", message.getId(), e);
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
      case PAYMENT_ORDER_CONFIRMATION ->
          throw new IllegalArgumentException("Message type not supported: " + messageType);
    };
  }

  private String extractMessageName(String namespace) {
    return namespace.substring(namespace.lastIndexOf(':') + 1);
  }

  private Optional<String> extractNamespace(String xml) {
    if (xml == null || xml.isBlank()) {
      throw new IllegalArgumentException("XML string cannot be null or empty");
    }

    try {
      XMLStreamReader reader =
          XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));

      try {
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            String namespace = reader.getNamespaceURI();
            return (namespace != null && !namespace.isEmpty())
                ? Optional.of(namespace)
                : Optional.empty();
          }
        }
        throw new IllegalArgumentException("No root element found in XML");
      } finally {
        reader.close();
      }
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException("Failed to parse XML: " + e.getMessage(), e);
    }
  }
}
