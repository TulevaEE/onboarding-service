package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.CUSTODY;
import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.IDENTITY;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  PopulationRegisterResponseStore.class,
  PopulationRegisterResponseStoreTest.FixedClockConfig.class
})
class PopulationRegisterResponseStoreTest {

  private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");
  private static final String PERSONAL_CODE = "48503150000";
  private static final List<Map<String, Object>> RESPONSE =
      List.of(Map.of("isikukood", PERSONAL_CODE, "eesnimi", "MARI"));

  @Autowired private TestEntityManager entityManager;
  @Autowired private PopulationRegisterResponseStore store;

  @Test
  void findsResponseWhileWithinMaxAge() {
    UUID messageId = UUID.randomUUID();
    persistAt(IDENTITY, NOW, RESPONSE, messageId);

    assertThat(store.findFresh(PERSONAL_CODE, IDENTITY, ofMinutes(15)))
        .contains(new StoredResponse(messageId, RESPONSE));
  }

  @Test
  void doesNotFindResponseOlderThanMaxAge() {
    persistAt(IDENTITY, NOW.minus(ofMinutes(16)), RESPONSE);

    assertThat(store.findFresh(PERSONAL_CODE, IDENTITY, ofMinutes(15))).isEmpty();
  }

  @Test
  void neverReusesResponseWhenMaxAgeIsZero() {
    persistAt(IDENTITY, NOW, RESPONSE);

    assertThat(store.findFresh(PERSONAL_CODE, IDENTITY, ZERO)).isEmpty();
  }

  @Test
  void separatesResponsesByQueryType() {
    UUID messageId = UUID.randomUUID();
    persistAt(CUSTODY, NOW, RESPONSE, messageId);

    assertThat(store.findFresh(PERSONAL_CODE, IDENTITY, ofMinutes(15))).isEmpty();
    assertThat(store.findFresh(PERSONAL_CODE, CUSTODY, ofMinutes(15)))
        .contains(new StoredResponse(messageId, RESPONSE));
  }

  @Test
  void findsTheMostRecentResponse() {
    List<Map<String, Object>> older = List.of(Map.of("eesnimi", "OLD"));
    UUID newestMessageId = UUID.randomUUID();
    persistAt(IDENTITY, NOW.minus(ofMinutes(10)), older);
    persistAt(IDENTITY, NOW.minus(ofMinutes(1)), RESPONSE, newestMessageId);

    assertThat(store.findFresh(PERSONAL_CODE, IDENTITY, ofMinutes(15)))
        .contains(new StoredResponse(newestMessageId, RESPONSE));
  }

  @Test
  void erasesResponseBodiesPastRetentionButKeepsAuditMetadata() {
    UUID messageId = UUID.randomUUID();
    Instant createdAt = NOW.minus(ofDays(366));
    Long id = persistAt(IDENTITY, createdAt, RESPONSE, messageId);

    int erased = store.eraseResponsesOlderThan(ofDays(365));

    entityManager.clear();
    PopulationRegisterResponse purged = entityManager.find(PopulationRegisterResponse.class, id);
    assertThat(erased).isEqualTo(1);
    assertThat(purged.getResponse()).isNull();
    assertThat(purged.getMessageId()).isEqualTo(messageId);
    assertThat(purged.getPersonalCode()).isEqualTo(PERSONAL_CODE);
    assertThat(purged.getQueryType()).isEqualTo(IDENTITY);
    assertThat(purged.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void keepsResponseBodiesWithinRetention() {
    Long id = persistAt(IDENTITY, NOW.minus(ofDays(364)), RESPONSE);

    int erased = store.eraseResponsesOlderThan(ofDays(365));

    entityManager.clear();
    assertThat(erased).isZero();
    assertThat(entityManager.find(PopulationRegisterResponse.class, id).getResponse())
        .isEqualTo(RESPONSE);
  }

  @Test
  void doesNotReuseAnErasedResponse() {
    persistAt(IDENTITY, NOW.minus(ofMinutes(1)), null);

    assertThat(store.findFresh(PERSONAL_CODE, IDENTITY, ofMinutes(15))).isEmpty();
  }

  private Long persistAt(
      PopulationRegisterQueryType queryType,
      Instant createdAt,
      List<Map<String, Object>> response) {
    return persistAt(queryType, createdAt, response, UUID.randomUUID());
  }

  private Long persistAt(
      PopulationRegisterQueryType queryType,
      Instant createdAt,
      List<Map<String, Object>> response,
      UUID messageId) {
    PopulationRegisterResponse entity =
        PopulationRegisterResponse.builder()
            .personalCode(PERSONAL_CODE)
            .queryType(queryType)
            .messageId(messageId)
            .response(response)
            .createdAt(createdAt)
            .build();
    entityManager.persist(entity);
    entityManager.flush();
    return entity.getId();
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
