package ee.tuleva.onboarding.populationregister;

import static ee.tuleva.onboarding.populationregister.PopulationRegisterQueryType.IDENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  PopulationRegisterResponseStore.class,
  PopulationRegisterResponseStoreTransactionTest.FixedClockConfig.class
})
class PopulationRegisterResponseStoreTransactionTest {

  private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");
  private static final String PERSONAL_CODE = "48503150000";

  @Autowired private PopulationRegisterResponseStore store;
  @Autowired private PopulationRegisterResponseRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void keepsTheAuditRowWhenTheCallingTransactionRollsBack() {
    var transactions = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      store.save(PERSONAL_CODE, IDENTITY, UUID.randomUUID(), List.of());
                      throw new PopulationRegisterException(
                          "Population register returned no person: personalCode=" + PERSONAL_CODE);
                    }))
        .isInstanceOf(PopulationRegisterException.class);

    assertThat(repository.findAll())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getPersonalCode()).isEqualTo(PERSONAL_CODE);
              assertThat(saved.getQueryType()).isEqualTo(IDENTITY);
              assertThat(saved.getResponse()).isEqualTo(List.of());
              assertThat(saved.getCreatedAt()).isEqualTo(NOW);
            });
  }

  @Test
  void keepsTheAuditRowWhenTheCallingTransactionCommits() {
    var transactions = new TransactionTemplate(transactionManager);
    List<Map<String, Object>> response = List.of(Map.of("isikukood", PERSONAL_CODE));

    transactions.executeWithoutResult(
        status -> store.save(PERSONAL_CODE, IDENTITY, UUID.randomUUID(), response));

    assertThat(repository.findAll()).singleElement().extracting("response").isEqualTo(response);
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
