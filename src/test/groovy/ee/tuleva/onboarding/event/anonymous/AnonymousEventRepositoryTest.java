package ee.tuleva.onboarding.event.anonymous;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
class AnonymousEventRepositoryTest {

  @Autowired private JdbcClient jdbcClient;

  private AnonymousEventRepository repository;

  @BeforeEach
  void setUp() {
    repository = new AnonymousEventRepository(jdbcClient, new ObjectMapper());
  }

  @Test
  void savesTheEventAndForgetsItAfterTheCutoff() {
    var command =
        AnonymousEventCommand.builder()
            .type(AnonymousEventType.LOGIN_FAILED)
            .visitId("8f1c2d3e-aaaa")
            .device("mobile")
            .data(Map.of("method", "SMART_ID", "reason", "smart.id.timeout"))
            .build();

    repository.save(command, Instant.parse("2025-10-01T10:00:00Z"));
    repository.save(command, Instant.parse("2026-11-25T10:00:00Z"));

    assertThat(count()).isEqualTo(2);
    assertThat(
            jdbcClient
                .sql("SELECT type FROM anonymous_event WHERE visit_id = '8f1c2d3e-aaaa' LIMIT 1")
                .query(String.class)
                .single())
        .isEqualTo("LOGIN_FAILED");

    int deleted = repository.deleteOlderThan(Instant.parse("2025-10-31T00:00:00Z"));

    assertThat(deleted).isEqualTo(1);
    assertThat(count()).isEqualTo(1);
  }

  private long count() {
    return jdbcClient.sql("SELECT count(*) FROM anonymous_event").query(Long.class).single();
  }
}
