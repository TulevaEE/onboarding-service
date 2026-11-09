package ee.tuleva.onboarding.auth.partner;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import({JdbcHandoverTokenUses.class, JdbcHandoverTokenUsesTest.FixedClockConfig.class})
@Transactional
class JdbcHandoverTokenUsesTest {

  private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
  private static final String ISSUER = "COOP PANK AS";
  private static final String TOKEN_ID = "2716758c-970f-479c-9e61-52ad3692faef";

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, UTC);
    }
  }

  @Autowired private JdbcHandoverTokenUses handoverTokenUses;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void aTokenIdIsAFirstUseOnlyOnce() {
    assertThat(handoverTokenUses.firstUse(ISSUER, TOKEN_ID)).isTrue();
    assertThat(handoverTokenUses.firstUse(ISSUER, TOKEN_ID)).isFalse();
  }

  @Test
  void theSameTokenIdFromAnotherIssuerIsItsOwnFirstUse() {
    handoverTokenUses.firstUse(ISSUER, TOKEN_ID);

    assertThat(handoverTokenUses.firstUse("ANOTHER BANK AS", TOKEN_ID)).isTrue();
  }

  @Test
  void aFirstUseIsRecordedWithItsTime() {
    handoverTokenUses.firstUse(ISSUER, TOKEN_ID);

    assertThat(recordedUses()).containsExactly(new RecordedUse(ISSUER, TOKEN_ID, NOW));
  }

  private record RecordedUse(String issuer, String tokenId, Instant spentAt) {}

  private List<RecordedUse> recordedUses() {
    return jdbcClient
        .sql("SELECT issuer, token_id, spent_at FROM partner_spent_handover_token")
        .query(
            (rs, rowNum) ->
                new RecordedUse(
                    rs.getString("issuer"),
                    rs.getString("token_id"),
                    rs.getObject("spent_at", OffsetDateTime.class).toInstant()))
        .list();
  }
}
