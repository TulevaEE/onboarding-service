package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
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
class LedgerSystemAccountConcurrencyIntegrationTest {

  @Autowired LedgerAccountService ledgerAccountService;
  @Autowired JdbcClient jdbcClient;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void concurrentGetOrCreateOfSameSystemAccount_createsExactlyOneAccount() throws Exception {
    assumeTrue(isPostgres(), "The get-or-create race only reproduces on PostgreSQL");

    String name = "CONCURRENT_SYSTEM_ACCOUNT:" + UUID.randomUUID();
    int threads = 16;
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    long accounts;
    try {
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    barrier.await();
                    transactionTemplate.executeWithoutResult(status -> getOrCreate(name));
                  } catch (Throwable t) {
                    errors.add(t);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(60, SECONDS);
      }
      accounts = systemAccountCount(name);
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(10, SECONDS);
      deleteSystemAccounts(name);
    }

    assertThat(errors).isEmpty();
    assertThat(accounts).isEqualTo(1);
  }

  private LedgerAccount getOrCreate(String name) {
    return ledgerAccountService
        .findSystemAccountByName(name, ASSET, EUR)
        .orElseGet(() -> ledgerAccountService.createSystemAccount(name, ASSET, EUR));
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }

  private long systemAccountCount(String name) {
    return jdbcClient
        .sql("SELECT count(*) FROM ledger.account WHERE name = :name AND owner_party_id IS NULL")
        .param("name", name)
        .query(Long.class)
        .single();
  }

  private void deleteSystemAccounts(String name) {
    jdbcClient
        .sql("DELETE FROM ledger.account WHERE name = :name AND owner_party_id IS NULL")
        .param("name", name)
        .update();
  }
}
