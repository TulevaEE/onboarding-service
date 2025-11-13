package ee.tuleva.onboarding.mandate.email.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandrillWebhookService {

  private final EmailRepository emailRepository;
  private final EventLogRepository eventLogRepository;
  private final MandrillSignatureVerifier signatureVerifier;
  private final ObjectMapper objectMapper;

  @Transactional
  public void handleWebhook(String mandrillEvents, String signature, HttpServletRequest request) {
    if (!signatureVerifier.verify(request, signature)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
    }

    List<MandrillWebhookEvent> events = parseEvents(mandrillEvents);
    processWebhookEvents(events);
  }

  public void processWebhookEvents(List<MandrillWebhookEvent> events) {
    log.info("Processing {} Mandrill webhook events", events.size());

    for (MandrillWebhookEvent event : events) {
      if (!event.isSupported()) {
        log.debug("Ignoring unsupported event type: {}", event.event());
        continue;
      }

      try {
        processWebhookEvent(event);
      } catch (Exception e) {
        log.error("Error processing Mandrill webhook event: {}", event.event(), e);
      }
    }
  }

  private List<MandrillWebhookEvent> parseEvents(String mandrillEvents) {
    try {
      return objectMapper.readValue(mandrillEvents, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.error("Failed to parse Mandrill webhook events", e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
    }
  }

  private void processWebhookEvent(MandrillWebhookEvent event) {
    if (event.msg() == null || event.msg().id() == null) {
      log.warn("Received Mandrill webhook event without message ID, ignoring");
      return;
    }

    String mandrillMessageId = event.msg().id();

    emailRepository
        .findByMandrillMessageId(mandrillMessageId)
        .ifPresentOrElse(
            email -> saveEventLog(event, email),
            () ->
                log.warn(
                    "Received Mandrill webhook event for unknown message ID: {}",
                    mandrillMessageId));
  }

  private void saveEventLog(MandrillWebhookEvent event, Email email) {
    if (!event.isSupported()) {
      log.debug("Ignoring unsupported Mandrill event type: {}", event.event());
      return;
    }

    Map<String, Object> eventData = buildEventData(event, email);
    Instant timestamp = Instant.ofEpochSecond(event.ts());
    String eventType = event.event();

    EventLog eventLog =
        EventLog.builder()
            .type(eventType)
            .principal(email.getPersonalCode())
            .timestamp(timestamp)
            .data(eventData)
            .build();

    eventLogRepository.save(eventLog);

    log.info(
        "Saved {} event for email ID {} (personalCode={}, mandrillMessageId={})",
        eventType,
        email.getId(),
        email.getPersonalCode(),
        email.getMandrillMessageId());
  }

  private Map<String, Object> buildEventData(MandrillWebhookEvent event, Email email) {
    Map<String, Object> data = new HashMap<>();

    data.put("mandrillMessageId", event.msg().id());
    data.put("emailType", email.getType().toString());

    if (event.url() != null) {
      data.put("url", event.url());
    }

    return data;
  }
}
