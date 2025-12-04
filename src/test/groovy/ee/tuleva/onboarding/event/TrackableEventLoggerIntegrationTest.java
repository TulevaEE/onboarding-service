package ee.tuleva.onboarding.event;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class TrackableEventLoggerIntegrationTest {

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private EventLogRepository eventLogRepository;

  @BeforeEach
  void setUp() {
    eventLogRepository.deleteAll();
  }

  @Test
  @Transactional
  @Commit
  void logsTrackableEvent() {
    // Given
    Person person = samplePerson;
    TrackableEventType eventType = TrackableEventType.LOGIN;
    Map<String, Object> eventData = Map.of("key", "value", "anotherKey", 123);
    TrackableEvent eventToPublish = new TrackableEvent(person, eventType, eventData);

    // When
    eventPublisher.publishEvent(eventToPublish);

    // Then
    Iterable<EventLog> eventLogs = eventLogRepository.findAll();
    assertThat(eventLogs).hasSize(1);

    EventLog savedLog = eventLogs.iterator().next();
    assertThat(savedLog.getPrincipal()).isEqualTo(person.getPersonalCode());
    assertThat(savedLog.getType()).isEqualTo(eventType.toString());
    assertThat(savedLog.getData()).isEqualTo(eventData);
    assertThat(savedLog.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @Transactional
  @Commit
  void logsTrackableEventWithNoData() {
    // Given
    Person person = sampleRetirementAgePerson;
    TrackableEventType eventType = TrackableEventType.MANDATE_SUCCESSFUL;
    TrackableEvent eventToPublish = new TrackableEvent(person, eventType);

    // When
    eventPublisher.publishEvent(eventToPublish);

    // Then
    Iterable<EventLog> eventLogs = eventLogRepository.findAll();
    assertThat(eventLogs).hasSize(1);

    EventLog savedLog = eventLogs.iterator().next();
    assertThat(savedLog.getPrincipal()).isEqualTo(person.getPersonalCode());
    assertThat(savedLog.getType()).isEqualTo(eventType.toString());
    assertThat(savedLog.getData()).isEmpty();
  }

  @Test
  @Transactional
  @Commit
  void logsTrackableSystemEvent() {
    TrackableEventType eventType = TrackableEventType.SUBSCRIPTION_BATCH_CREATED;
    Map<String, Object> eventData = Map.of("batchId", "test-batch-id", "paymentCount", 3);
    TrackableSystemEvent eventToPublish = new TrackableSystemEvent(eventType, eventData);

    eventPublisher.publishEvent(eventToPublish);

    Iterable<EventLog> eventLogs = eventLogRepository.findAll();
    assertThat(eventLogs).hasSize(1);

    EventLog savedLog = eventLogs.iterator().next();
    assertThat(savedLog.getPrincipal()).isEqualTo("onboarding-service");
    assertThat(savedLog.getType()).isEqualTo(eventType.toString());
    assertThat(savedLog.getData()).isEqualTo(eventData);
    assertThat(savedLog.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
  }
}
