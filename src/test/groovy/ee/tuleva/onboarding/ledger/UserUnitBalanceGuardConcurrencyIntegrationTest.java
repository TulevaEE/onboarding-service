package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import java.math.BigDecimal;
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

/**
 * The races the guard exists for are the concurrent ones, and neither is covered by {@code
 * RedemptionBatchJob}'s {@code @SchedulerLock}: two redemption requests from the same party (two
 * devices, a double click) both read the same available balance before either reserves, and a
 * cancellation interleaves with the batch pricing the same request.
 *
 * <p>Both need two separately committed transactions against real row locks, so they only reproduce
 * on PostgreSQL.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.datasource.hikari.maximum-pool-size=20",
      "ledger.unit-balance-guard.enforce=true"
    })
class UserUnitBalanceGuardConcurrencyIntegrationTest {

  private static final BigDecimal HELD_UNITS = new BigDecimal("10.00000");
  private static final BigDecimal NAV = new BigDecimal("10.0000");
  private static final BigDecimal CASH = new BigDecimal("100.00");

  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired JdbcClient jdbcClient;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void twoConcurrentReservationsOfTheSameUnits_onlyOneSucceeds() throws Exception {
    assumeTrue(isPostgres(), "Row locks only serialise for real on PostgreSQL");

    String ownerId = "conc-" + UUID.randomUUID();
    PartyRef party = LedgerRefs.from(new PartyId(LEGAL_ENTITY, ownerId));
    var transactionTemplate = new TransactionTemplate(transactionManager);

    long succeeded;
    BigDecimal unitsBalance;
    try {
      transactionTemplate.executeWithoutResult(status -> giveTheParty(party));

      var errors =
          runConcurrently(
              2,
              index ->
                  transactionTemplate.executeWithoutResult(
                      status ->
                          savingsFundLedger.reserveFundUnitsForRedemption(
                              party, HELD_UNITS, UUID.randomUUID())));

      succeeded = 2 - errors.size();
      unitsBalance = balanceOf(ownerId, "FUND_UNITS");
    } finally {
      deleteParty(ownerId);
    }

    assertThat(succeeded).isEqualTo(1);
    assertThat(unitsBalance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void cancellingWhileTheBatchPricesTheSameReservation_onlyOneSucceeds() throws Exception {
    assumeTrue(isPostgres(), "Row locks only serialise for real on PostgreSQL");

    String ownerId = "conc-" + UUID.randomUUID();
    PartyRef party = LedgerRefs.from(new PartyId(LEGAL_ENTITY, ownerId));
    var transactionTemplate = new TransactionTemplate(transactionManager);

    long succeeded;
    BigDecimal reservedBalance;
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            giveTheParty(party);
            savingsFundLedger.reserveFundUnitsForRedemption(party, HELD_UNITS, UUID.randomUUID());
          });

      var errors =
          runConcurrently(
              2,
              index -> {
                if (index == 0) {
                  transactionTemplate.executeWithoutResult(
                      status ->
                          savingsFundLedger.cancelRedemptionReservation(
                              party, HELD_UNITS, UUID.randomUUID()));
                } else {
                  transactionTemplate.executeWithoutResult(
                      status ->
                          savingsFundLedger.redeemFundUnitsFromReserved(
                              party, HELD_UNITS, CASH, NAV, UUID.randomUUID()));
                }
              });

      succeeded = 2 - errors.size();
      reservedBalance = balanceOf(ownerId, "FUND_UNITS_RESERVED");
    } finally {
      deleteParty(ownerId);
    }

    assertThat(succeeded).isEqualTo(1);
    assertThat(reservedBalance).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private void giveTheParty(PartyRef party) {
    savingsFundLedger.recordPaymentReceived(party, CASH, UUID.randomUUID());
    savingsFundLedger.reservePaymentForSubscription(party, CASH, UUID.randomUUID());
    savingsFundLedger.issueFundUnitsFromReserved(party, CASH, HELD_UNITS, NAV, UUID.randomUUID());
  }

  private List<Throwable> runConcurrently(int threads, ThrowingIntConsumer body) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CyclicBarrier barrier = new CyclicBarrier(threads);
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        int index = i;
        futures.add(
            pool.submit(
                () -> {
                  try {
                    barrier.await();
                    body.accept(index);
                  } catch (Throwable t) {
                    errors.add(t);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(60, SECONDS);
      }
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(10, SECONDS);
    }
    return errors;
  }

  private interface ThrowingIntConsumer {
    void accept(int index) throws Exception;
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }

  private BigDecimal balanceOf(String ownerId, String accountName) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM ledger.entry e
            JOIN ledger.account a ON a.id = e.account_id
            JOIN ledger.party p ON p.id = a.owner_party_id
            WHERE p.owner_id = :ownerId AND a.name = :accountName
            """)
        .param("ownerId", ownerId)
        .param("accountName", accountName)
        .query(BigDecimal.class)
        .single();
  }

  private void deleteParty(String ownerId) {
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.entry WHERE account_id IN
              (SELECT a.id FROM ledger.account a JOIN ledger.party p ON p.id = a.owner_party_id
               WHERE p.owner_id = :ownerId)
            """)
        .param("ownerId", ownerId)
        .update();
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
