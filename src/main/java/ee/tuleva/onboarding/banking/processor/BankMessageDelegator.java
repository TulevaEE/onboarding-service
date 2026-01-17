package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.message.BankMessageType.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankMessageDelegator {

  private final Clock clock;
  private final BankingMessageRepository bankingMessageRepository;
  private final BankStatementExtractor bankStatementExtractor;
  private final ApplicationEventPublisher eventPublisher;

  // @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "BankMessageDelegator_processMessages",
      lockAtMostFor = "50s",
      lockAtLeastFor = "5s")
  public void processMessages() {
    var messages =
        bankingMessageRepository.findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc();

    for (BankingMessage message : messages) {
      processMessage(message);
    }
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
        var event =
            new BankStatementReceived(message.getId(), message.getBankType(), bankStatement);
        eventPublisher.publishEvent(event);
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
