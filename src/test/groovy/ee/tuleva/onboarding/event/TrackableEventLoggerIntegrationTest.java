package ee.tuleva.onboarding.event;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TrackableEventLogger.class)
@ExtendWith(OutputCaptureExtension.class)
class TrackableEventLoggerIntegrationTest {

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private EventLogRepository eventLogRepository;

  @Test
  void logsTrackableEvent() {
    // Given
    Person person = samplePerson;
    TrackableEventType eventType = TrackableEventType.LOGIN;
    Map<String, Object> eventData = Map.of("key", "value", "anotherKey", 123);
    TrackableEvent eventToPublish = new TrackableEvent(person, eventType, eventData);

    // When
    eventPublisher.publishEvent(eventToPublish);

    // Then
    assertThat(eventLogRepository.findAll())
        .filteredOn(
            log ->
                log.getType().equals(eventType.toString())
                    && person.getPersonalCode().equals(log.getPrincipal()))
        .singleElement()
        .satisfies(
            savedLog -> {
              assertThat(savedLog.getPrincipal()).isEqualTo(person.getPersonalCode());
              assertThat(savedLog.getData()).isEqualTo(eventData);
              assertThat(savedLog.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
            });
  }

  @Test
  void logsTrackableEventWithNoData() {
    // Given
    Person person = sampleRetirementAgePerson;
    TrackableEventType eventType = TrackableEventType.MANDATE_SUCCESSFUL;
    TrackableEvent eventToPublish = new TrackableEvent(person, eventType);

    // When
    eventPublisher.publishEvent(eventToPublish);

    // Then
    assertThat(eventLogRepository.findAll())
        .filteredOn(
            log ->
                log.getType().equals(eventType.toString())
                    && person.getPersonalCode().equals(log.getPrincipal()))
        .singleElement()
        .satisfies(
            savedLog -> {
              assertThat(savedLog.getPrincipal()).isEqualTo(person.getPersonalCode());
              assertThat(savedLog.getData()).isEmpty();
            });
  }

  @Test
  void logsTrackableSystemEvent() {
    TrackableEventType eventType = TrackableEventType.SUBSCRIPTION_BATCH_CREATED;
    String batchId = "test-batch-id";
    Map<String, Object> eventData = Map.of("batchId", batchId, "paymentCount", 3);
    TrackableSystemEvent eventToPublish = new TrackableSystemEvent(eventType, eventData);

    eventPublisher.publishEvent(eventToPublish);

    assertThat(eventLogRepository.findAll())
        .filteredOn(
            log ->
                log.getType().equals(eventType.toString())
                    && log.getData() != null
                    && batchId.equals(log.getData().get("batchId")))
        .singleElement()
        .satisfies(
            savedLog -> {
              assertThat(savedLog.getPrincipal()).isEqualTo("onboarding-service");
              assertThat(savedLog.getData()).isEqualTo(eventData);
              assertThat(savedLog.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
            });
  }

  @Test
  void persistsTheClientConnectionOfALoginButKeepsItOutOfTheApplicationLog(CapturedOutput output) {
    Person person = samplePerson;
    Map<String, Object> eventData =
        Map.of(
            "method", "SMART_ID",
            "ipAddress", "198.51.100.23",
            "userAgent", "Mozilla/5.0 (TestBrowser/123.4)");

    eventPublisher.publishEvent(new TrackableEvent(person, TrackableEventType.LOGIN, eventData));

    assertThat(eventLogRepository.findAll())
        .filteredOn(
            log -> log.getData() != null && "198.51.100.23".equals(log.getData().get("ipAddress")))
        .singleElement()
        .satisfies(savedLog -> assertThat(savedLog.getData()).isEqualTo(eventData));
    assertThat(output.getAll())
        .contains("SMART_ID")
        .doesNotContain("198.51.100.23")
        .doesNotContain("TestBrowser/123.4");
  }
}
