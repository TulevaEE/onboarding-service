package ee.tuleva.onboarding.swedbank.processor;

import ee.tuleva.onboarding.banking.message.BankMessageType;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import java.io.StringReader;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwedbankMessageDelegator {

  private final Clock clock;
  private final BankingMessageRepository bankingMessageRepository;
  private final List<SwedbankMessageProcessor> messageProcessors;

  // @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "SwedbankMessageDelegator_processMessages",
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

      var supportedProcessor =
          messageProcessors.stream()
              .filter(processor -> processor.supports(messageType))
              .findFirst();

      if (supportedProcessor.isEmpty()) {
        log.info(
            "No processor found for message type: {} (message id: {})",
            messageType,
            message.getId());
      } else {
        supportedProcessor.get().processMessage(message.getRawResponse(), messageType);
      }

      message.setProcessedAt(clock.instant());
      bankingMessageRepository.save(message);
    } catch (Exception e) {
      log.error("Failed to process message id: {}", message.getId(), e);
      message.setFailedAt(clock.instant());
      bankingMessageRepository.save(message);
    }
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
        // Find the first START_ELEMENT (root element)
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
