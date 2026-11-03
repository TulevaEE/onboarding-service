package ee.tuleva.onboarding.oauth.server;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class PairwiseSubjects {

  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final TransactionTemplate insertInItsOwnTransaction;

  PairwiseSubjects(
      JdbcClient jdbcClient, Clock clock, PlatformTransactionManager transactionManager) {
    this.jdbcClient = jdbcClient;
    this.clock = clock;
    this.insertInItsOwnTransaction = new TransactionTemplate(transactionManager);
    this.insertInItsOwnTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
  }

  UUID subjectFor(String clientId, String personalCode) {
    return find(clientId, personalCode).orElseGet(() -> create(clientId, personalCode));
  }

  private UUID create(String clientId, String personalCode) {
    var id = UUID.randomUUID();
    try {
      insertInItsOwnTransaction.executeWithoutResult(
          status ->
              jdbcClient
                  .sql(
                      """
                      INSERT INTO oauth_subject (id, client_id, personal_code, created_at)
                      VALUES (:id, :clientId, :personalCode, :createdAt)
                      """)
                  .param("id", id)
                  .param("clientId", clientId)
                  .param("personalCode", personalCode)
                  .param("createdAt", Timestamps.of(clock.instant()))
                  .update());
      return id;
    } catch (DuplicateKeyException createdByAConcurrentApproval) {
      return find(clientId, personalCode).orElseThrow();
    }
  }

  private Optional<UUID> find(String clientId, String personalCode) {
    return jdbcClient
        .sql(
            "SELECT id FROM oauth_subject WHERE client_id = :clientId AND personal_code = :personalCode")
        .param("clientId", clientId)
        .param("personalCode", personalCode)
        .query(UUID.class)
        .optional();
  }
}
