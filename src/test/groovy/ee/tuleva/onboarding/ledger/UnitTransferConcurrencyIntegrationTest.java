package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(SavingsFundLedgerStackConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=20")
class UnitTransferConcurrencyIntegrationTest {

  private static final BigDecimal ONE_UNIT = new BigDecimal("1.00000");
  private static final LocalDate PRICED_ON = LocalDate.parse("2025-03-10");

  @Autowired LedgerService ledgerService;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired JdbcClient jdbcClient;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void twoTransfersApprovedAtOnceNeverMoveMoreThanTheGiverStillHasInvested() throws Exception {
    assumeTrue(isPostgres(), "Concurrent transactions only reproduce on PostgreSQL");

    PartyRef giver = new PartyRef(LEGAL_ENTITY, "utc-giver-" + randomUUID());
    PartyRef firstReceiver = new PartyRef(LEGAL_ENTITY, "utc-first-" + randomUUID());
    PartyRef secondReceiver = new PartyRef(LEGAL_ENTITY, "utc-second-" + randomUUID());
    List<PartyRef> parties = List.of(giver, firstReceiver, secondReceiver);

    BigDecimal giverSubscriptions;
    BigDecimal firstReceiverSubscriptions;
    BigDecimal secondReceiverSubscriptions;
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      parties.forEach(ledgerService::initializeAccounts);
      givenTheGiverBought(giver, new BigDecimal("10.01"), new BigDecimal("2.00000"));

      CyclicBarrier barrier = new CyclicBarrier(2);
      TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
      List<Future<?>> futures = new ArrayList<>();

      for (PartyRef receiver : List.of(firstReceiver, secondReceiver)) {
        futures.add(
            pool.submit(
                () -> {
                  try {
                    transactionTemplate.executeWithoutResult(
                        status -> {
                          savingsFundLedger.quoteUnitTransfer(giver, receiver, ONE_UNIT);
                          bothHaveRead(barrier);
                          savingsFundLedger.recordUnitTransfer(
                              new UnitTransferInstruction(
                                  giver, receiver, ONE_UNIT, ZERO, randomUUID()));
                        });
                  } catch (Throwable anything) {
                    errors.add(anything);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(60, SECONDS);
      }

      giverSubscriptions = holding(giver, SUBSCRIPTIONS);
      firstReceiverSubscriptions = holding(firstReceiver, SUBSCRIPTIONS);
      secondReceiverSubscriptions = holding(secondReceiver, SUBSCRIPTIONS);
    } finally {
      pool.shutdownNow();
      deleteParties(parties);
    }

    assertThat(errors).isEmpty();
    assertThat(giverSubscriptions).isEqualByComparingTo("0.00");
    assertThat(firstReceiverSubscriptions.add(secondReceiverSubscriptions))
        .isEqualByComparingTo("10.01");
  }

  @Test
  void twoTransfersInOppositeDirectionsAtOnceBothGoThrough() throws Exception {
    assumeTrue(isPostgres(), "Concurrent transactions only reproduce on PostgreSQL");

    PartyRef first = new PartyRef(LEGAL_ENTITY, "utc-one-" + randomUUID());
    PartyRef second = new PartyRef(LEGAL_ENTITY, "utc-other-" + randomUUID());
    List<PartyRef> parties = List.of(first, second);

    BigDecimal firstUnits;
    BigDecimal secondUnits;
    BigDecimal firstSubscriptions;
    BigDecimal secondSubscriptions;
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      parties.forEach(ledgerService::initializeAccounts);
      givenTheGiverBought(first, new BigDecimal("100.00"), new BigDecimal("10.00000"));
      givenTheGiverBought(second, new BigDecimal("200.00"), new BigDecimal("20.00000"));

      CyclicBarrier barrier = new CyclicBarrier(2);
      TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
      List<Future<?>> futures = new ArrayList<>();

      for (List<PartyRef> direction : List.of(parties, parties.reversed())) {
        PartyRef giver = direction.getFirst();
        PartyRef receiver = direction.getLast();
        futures.add(
            pool.submit(
                () -> {
                  try {
                    transactionTemplate.executeWithoutResult(
                        status -> {
                          savingsFundLedger.quoteUnitTransfer(giver, receiver, ONE_UNIT);
                          bothHaveRead(barrier);
                          savingsFundLedger.recordUnitTransfer(
                              new UnitTransferInstruction(
                                  giver, receiver, ONE_UNIT, ZERO, randomUUID()));
                        });
                  } catch (Throwable anything) {
                    errors.add(anything);
                  }
                  return null;
                }));
      }
      for (Future<?> future : futures) {
        future.get(60, SECONDS);
      }

      firstUnits = holding(first, FUND_UNITS);
      secondUnits = holding(second, FUND_UNITS);
      firstSubscriptions = holding(first, SUBSCRIPTIONS);
      secondSubscriptions = holding(second, SUBSCRIPTIONS);
    } finally {
      pool.shutdownNow();
      deleteParties(parties);
    }

    assertThat(errors).isEmpty();
    assertThat(firstUnits).isEqualByComparingTo("10.00000");
    assertThat(secondUnits).isEqualByComparingTo("20.00000");
    assertThat(firstSubscriptions).isEqualByComparingTo("100.00");
    assertThat(secondSubscriptions).isEqualByComparingTo("200.00");
  }

  @Test
  void aTransferDoesNotWaitForABatchThatIsOnlyBookingEntriesOfTheSameSaver() throws Exception {
    assumeTrue(isPostgres(), "Row lock modes only differ on PostgreSQL");

    PartyRef giver = new PartyRef(LEGAL_ENTITY, "utc-locked-" + randomUUID());
    PartyRef receiver = new PartyRef(LEGAL_ENTITY, "utc-waiting-" + randomUUID());
    List<PartyRef> parties = List.of(giver, receiver);

    BigDecimal receiverUnits;
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Connection batch = dataSource.getConnection();
    Future<?> transfer = null;
    try {
      parties.forEach(ledgerService::initializeAccounts);
      givenTheGiverBought(giver, new BigDecimal("100.00"), new BigDecimal("10.00000"));
      batch.setAutoCommit(false);
      referenceTheAccountsOf(batch, giver);

      TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
      transfer =
          pool.submit(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status ->
                          savingsFundLedger.recordUnitTransfer(
                              new UnitTransferInstruction(
                                  giver, receiver, ONE_UNIT, ZERO, randomUUID()))));

      transfer.get(20, SECONDS);
      receiverUnits = holding(receiver, FUND_UNITS);
    } finally {
      batch.rollback();
      batch.close();
      if (transfer != null) {
        transfer.cancel(true);
      }
      pool.shutdownNow();
      pool.awaitTermination(60, SECONDS);
      deleteParties(parties);
    }

    assertThat(receiverUnits).isEqualByComparingTo("1.00000");
  }

  private static void referenceTheAccountsOf(Connection batch, PartyRef party) throws SQLException {
    try (var statement =
        batch.prepareStatement(
            """
            SELECT a.id FROM ledger.account a
              JOIN ledger.party p ON p.id = a.owner_party_id
             WHERE p.owner_id = ?
               FOR KEY SHARE
            """)) {
      statement.setString(1, party.code());
      statement.executeQuery().close();
    }
  }

  private static void bothHaveRead(CyclicBarrier barrier) {
    try {
      barrier.await(60, SECONDS);
    } catch (Exception interrupted) {
      throw new IllegalStateException(interrupted);
    }
  }

  private void givenTheGiverBought(PartyRef giver, BigDecimal cashAmount, BigDecimal fundUnits) {
    UUID paymentId = randomUUID();
    BigDecimal boughtAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    savingsFundLedger.recordPaymentReceived(giver, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(giver, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        giver, cashAmount, fundUnits, boughtAt, PRICED_ON, paymentId);
  }

  private BigDecimal holding(PartyRef party, UserAccount userAccount) {
    return jdbcClient
        .sql(
            """
            SELECT COALESCE(SUM(e.amount), 0) FROM ledger.entry e
              JOIN ledger.account a ON a.id = e.account_id
              JOIN ledger.party p ON p.id = a.owner_party_id
             WHERE p.owner_id = :ownerId AND a.name = :accountName
            """)
        .param("ownerId", party.code())
        .param("accountName", userAccount.name())
        .query(BigDecimal.class)
        .single()
        .negate();
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }

  private void deleteParties(List<PartyRef> parties) {
    List<String> ownerIds = parties.stream().map(PartyRef::code).toList();
    List<UUID> transactionIds =
        jdbcClient
            .sql(
                """
                SELECT DISTINCT e.transaction_id FROM ledger.entry e
                  JOIN ledger.account a ON a.id = e.account_id
                  JOIN ledger.party p ON p.id = a.owner_party_id
                 WHERE p.owner_id IN (:ownerIds)
                """)
            .param("ownerIds", ownerIds)
            .query(UUID.class)
            .list();
    if (!transactionIds.isEmpty()) {
      jdbcClient
          .sql("DELETE FROM ledger.entry WHERE transaction_id IN (:transactionIds)")
          .param("transactionIds", transactionIds)
          .update();
      jdbcClient
          .sql("DELETE FROM ledger.transaction WHERE id IN (:transactionIds)")
          .param("transactionIds", transactionIds)
          .update();
    }
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.account WHERE owner_party_id IN
              (SELECT id FROM ledger.party WHERE owner_id IN (:ownerIds))
            """)
        .param("ownerIds", ownerIds)
        .update();
    jdbcClient
        .sql("DELETE FROM ledger.party WHERE owner_id IN (:ownerIds)")
        .param("ownerIds", ownerIds)
        .update();
  }
}
