package ee.tuleva.onboarding.mandate.email.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.event.EventLog
import ee.tuleva.onboarding.event.EventLogRepository
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SENT
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_MANDATE
import static ee.tuleva.onboarding.mandate.email.webhook.MandrillWebhookEvent.MandrillMessage

class MandrillWebhookServiceSpec extends Specification {

  EmailRepository emailRepository = Mock()
  EventLogRepository eventLogRepository = Mock()
  MandrillSignatureVerifier signatureVerifier = Mock()
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
  MandrillWebhookService service = new MandrillWebhookService(emailRepository, eventLogRepository, signatureVerifier, objectMapper)
  HttpServletRequest request = Mock()

  String mandrillMessageId = "mandrill_msg_123"
  String personalCode = "38512121234"
  Long emailId = 42L
  Long eventTimestamp = 1699900000L

  def "handleWebhook processes valid request"() {
    given:
    def eventsJson = objectMapper.writeValueAsString([
        [event: "open", _id: "event123", ts: eventTimestamp, msg: [_id: mandrillMessageId, email: "test@example.com", subject: "Subject", metadata: [:]], url: null]
    ])
    def email = sampleEmail()

    signatureVerifier.verify(request, "valid_signature") >> true
    emailRepository.findByMandrillMessageId(mandrillMessageId) >> Optional.of(email)

    when:
    service.handleWebhook(eventsJson, "valid_signature", request)

    then:
    1 * eventLogRepository.save(_)
  }

  def "handleWebhook rejects invalid signature"() {
    given:
    signatureVerifier.verify(request, "invalid_signature") >> false

    when:
    service.handleWebhook("[]", "invalid_signature", request)

    then:
    thrown(ResponseStatusException)
    0 * eventLogRepository.save(_)
  }

  def "handleWebhook rejects invalid JSON"() {
    given:
    signatureVerifier.verify(request, "valid_signature") >> true

    when:
    service.handleWebhook("{ invalid json }", "valid_signature", request)

    then:
    thrown(ResponseStatusException)
    0 * eventLogRepository.save(_)
  }

  def "processes open event and saves to event log"() {
    given:
    def email = sampleEmail()
    def event = openEvent()

    emailRepository.findByMandrillMessageId(mandrillMessageId) >> Optional.of(email)

    when:
    service.processWebhookEvents([event])

    then:
    1 * eventLogRepository.save({ EventLog log ->
      log.type == "OPEN" &&
          log.principal == personalCode &&
          log.timestamp == Instant.ofEpochSecond(eventTimestamp) &&
          log.data.mandrillMessageId == mandrillMessageId &&
          log.data.emailType == SECOND_PILLAR_MANDATE.toString()
    })
  }

  def "processes click event and saves to event log with URL"() {
    given:
    def email = sampleEmail()
    def event = clickEvent()

    emailRepository.findByMandrillMessageId(mandrillMessageId) >> Optional.of(email)

    when:
    service.processWebhookEvents([event])

    then:
    1 * eventLogRepository.save({ EventLog log ->
      log.type == "CLICK" &&
          log.principal == personalCode &&
          log.data.url == "https://tuleva.ee/en/2nd-pillar" &&
          log.data.mandrillMessageId == mandrillMessageId
    })
  }


  def "ignores event when email not found"() {
    given:
    def event = openEvent()
    emailRepository.findByMandrillMessageId(mandrillMessageId) >> Optional.empty()

    when:
    service.processWebhookEvents([event])

    then:
    0 * eventLogRepository.save(_)
  }

  def "ignores event without message ID"() {
    given:
    def event = MandrillWebhookEvent.builder()
        .event("open")
        .id("event_123")
        .ts(eventTimestamp)
        .build()

    when:
    service.processWebhookEvents([event])

    then:
    0 * emailRepository.findByMandrillMessageId(_)
    0 * eventLogRepository.save(_)
  }

  def "ignores unsupported event types"() {
    given:
    def email = sampleEmail()
    def event = MandrillWebhookEvent.builder()
        .event("bounce") // Unsupported event type
        .id("event_123")
        .ts(eventTimestamp)
        .msg(MandrillMessage.builder()
            .id(mandrillMessageId)
            .email("test@example.com")
            .subject("Subject")
            .build())
        .build()

    emailRepository.findByMandrillMessageId(mandrillMessageId) >> Optional.of(email)

    when:
    service.processWebhookEvents([event])

    then:
    0 * eventLogRepository.save(_)
  }

  def "handles events with minimal data"() {
    given:
    def email = sampleEmail()
    def event = MandrillWebhookEvent.builder()
        .event("open")
        .id("event_123")
        .ts(eventTimestamp)
        .msg(MandrillMessage.builder()
            .id(mandrillMessageId)
            .email("test@example.com")
            .subject("Subject")
            .build())
        .build()

    emailRepository.findByMandrillMessageId(mandrillMessageId) >> Optional.of(email)

    when:
    service.processWebhookEvents([event])

    then:
    1 * eventLogRepository.save({ EventLog log ->
      log.type == "OPEN" &&
          log.data.mandrillMessageId == mandrillMessageId &&
          log.data.emailType == SECOND_PILLAR_MANDATE.name() &&
          !log.data.containsKey("url")
    })
  }

  private Email sampleEmail() {
    Email.builder()
        .id(emailId)
        .personalCode(personalCode)
        .mandrillMessageId(mandrillMessageId)
        .type(SECOND_PILLAR_MANDATE)
        .status(SENT)
        .createdDate(Instant.now())
        .updatedDate(Instant.now())
        .build()
  }

  private MandrillWebhookEvent openEvent() {
    MandrillWebhookEvent.builder()
        .event("open")
        .id("event_123")
        .ts(eventTimestamp)
        .msg(MandrillMessage.builder()
            .id(mandrillMessageId)
            .email("test@example.com")
            .subject("Subject")
            .build())
        .build()
  }

  private MandrillWebhookEvent clickEvent() {
    MandrillWebhookEvent.builder()
        .event("click")
        .id("event_123")
        .ts(eventTimestamp)
        .msg(MandrillMessage.builder()
            .id(mandrillMessageId)
            .email("test@example.com")
            .subject("Subject")
            .build())
        .url("https://tuleva.ee/en/2nd-pillar")
        .build()
  }
}
