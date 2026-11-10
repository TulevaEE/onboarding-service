package ee.tuleva.onboarding.auth.partner;

import static java.time.ZoneOffset.UTC;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JdbcHandoverTokenUses implements HandoverTokenUses {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  @Override
  public boolean firstUse(String issuer, String tokenId) {
    try {
      jdbcClient
          .sql(
              "INSERT INTO partner_spent_handover_token (issuer, token_id, spent_at)"
                  + " VALUES (:issuer, :tokenId, :spentAt)")
          .param("issuer", issuer)
          .param("tokenId", tokenId)
          .param("spentAt", clock.instant().atOffset(UTC))
          .update();
      return true;
    } catch (DuplicateKeyException alreadyUsed) {
      return false;
    }
  }
}
