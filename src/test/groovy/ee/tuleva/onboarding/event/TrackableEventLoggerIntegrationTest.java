package ee.tuleva.onboarding.event;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
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
        .filteredOn(log -> log.getType().equals(eventType.toString()))
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
        .filteredOn(log -> log.getType().equals(eventType.toString()))
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
    Map<String, Object> eventData = Map.of("batchId", "test-batch-id", "paymentCount", 3);
    TrackableSystemEvent eventToPublish = new TrackableSystemEvent(eventType, eventData);

    eventPublisher.publishEvent(eventToPublish);

    assertThat(eventLogRepository.findAll())
        .filteredOn(log -> log.getType().equals(eventType.toString()))
        .singleElement()
        .satisfies(
            savedLog -> {
              assertThat(savedLog.getPrincipal()).isEqualTo("onboarding-service");
              assertThat(savedLog.getData()).isEqualTo(eventData);
              assertThat(savedLog.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
            });
  }
}
