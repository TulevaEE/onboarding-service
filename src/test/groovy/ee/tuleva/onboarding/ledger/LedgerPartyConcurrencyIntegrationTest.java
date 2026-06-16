package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=20")
class LedgerPartyConcurrencyIntegrationTest {

  @Autowired LedgerService ledgerService;
  @Autowired JdbcClient jdbcClient;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void concurrentGetPartyAccountForSameOwner_createsExactlyOnePartyAndAccount() throws Exception {
    assumeTrue(isPostgres(), "The get-or-create race only reproduces on PostgreSQL");

    String ownerId = "conc-" + UUID.randomUUID();
    int threads = 16;
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    long parties;
    long accounts;
    try {
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    barrier.await();
                    transactionTemplate.executeWithoutResult(
                        status ->
                            ledgerService.getPartyAccount(ownerId, LEGAL_ENTITY, SUBSCRIPTIONS));
                  } catch (Throwable t) {
                    errors.add(t);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(60, SECONDS);
      }
      parties = partyCount(ownerId);
      accounts = accountCount(ownerId);
    } finally {
      pool.shutdownNow();
      deleteParty(ownerId);
    }

    assertThat(errors).isEmpty();
    assertThat(parties).isEqualTo(1);
    assertThat(accounts).isEqualTo(1);
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }

  private long partyCount(String ownerId) {
    return jdbcClient
        .sql("SELECT count(*) FROM ledger.party WHERE owner_id = :ownerId")
        .param("ownerId", ownerId)
        .query(Long.class)
        .single();
  }

  private long accountCount(String ownerId) {
    return jdbcClient
        .sql(
            "SELECT count(*) FROM ledger.account a"
                + " JOIN ledger.party p ON p.id = a.owner_party_id"
                + " WHERE p.owner_id = :ownerId")
        .param("ownerId", ownerId)
        .query(Long.class)
        .single();
  }

  private void deleteParty(String ownerId) {
    jdbcClient
        .sql(
            "DELETE FROM ledger.account WHERE owner_party_id IN"
                + " (SELECT id FROM ledger.party WHERE owner_id = :ownerId)")
        .param("ownerId", ownerId)
        .update();
    jdbcClient
        .sql("DELETE FROM ledger.party WHERE owner_id = :ownerId")
        .param("ownerId", ownerId)
        .update();
  }
}
