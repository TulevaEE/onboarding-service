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
class AccountOpenedEmailClaims {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  boolean claim(String accountCode) {
    try {
      jdbcClient
          .sql(
              """
              INSERT INTO savings_fund_account_opened_email_claim (code, created_at)
              VALUES (:code, :createdAt)
              """)
          .param("code", accountCode)
          .param("createdAt", OffsetDateTime.now(clock))
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void release(String accountCode) {
    jdbcClient
        .sql("DELETE FROM savings_fund_account_opened_email_claim WHERE code = :code")
        .param("code", accountCode)
        .update();
  }
}
