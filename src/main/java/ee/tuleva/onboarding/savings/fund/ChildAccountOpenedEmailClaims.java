package ee.tuleva.onboarding.savings.fund;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ChildAccountOpenedEmailClaims {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  boolean claim(String childCode) {
    try {
      jdbcClient
          .sql(
              """
              INSERT INTO child_account_opened_email_claim (child_code, created_at)
              VALUES (:childCode, :createdAt)
              """)
          .param("childCode", childCode)
          .param("createdAt", OffsetDateTime.now(clock))
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }
}
