package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.ledger.GeneralLedgerRows.JournalEntryVersion;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(GeneralLedgerStackConfiguration.class)
@Transactional(propagation = NOT_SUPPORTED)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=20")
class GeneralLedgerConcurrencyIntegrationTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String SOURCE = "TEST_SYSTEM";
  private static final double MAX_DELETION_SHARE = 0.05;
  private static final int OVERLAPPING_RUNS = 8;
  private static final int PARTS = 20;

  @Autowired GeneralLedger generalLedger;
  @Autowired GeneralLedgerRows rows;
  @Autowired DataSource dataSource;

  @AfterEach
  void tearDown() {
    rows.deleteAll();
  }

  @Test
  void overlappingMirrorsLeaveOneLiveVersionPerPart() throws Exception {
    assumeTrue(isPostgres(), "The advisory lock is a no-op on H2");
    generalLedger.upsertAccounts(
        ENTITY,
        List.of(
            new GeneralLedgerAccount("100100", ASSET, Map.of("name", "Bank")),
            new GeneralLedgerAccount("400100", INCOME, Map.of("name", "Fee income"))));
    var book = book("10.00");
    var editedBook = book("12.00");

    var firstRunErrors = mirrorConcurrently(book);
    var postedVersions = versionsOf(book);
    var secondRunErrors = mirrorConcurrently(editedBook);
    var revisedVersions = versionsOf(editedBook);

    assertThat(firstRunErrors).isEmpty();
    assertThat(postedVersions)
        .allSatisfy(
            versions ->
                assertThat(versions).extracting(JournalEntryVersion::live).containsExactly(true));
    assertThat(secondRunErrors).isEmpty();
    assertThat(revisedVersions)
        .allSatisfy(
            versions ->
                assertThat(versions)
                    .extracting(JournalEntryVersion::live)
                    .containsExactly(false, true));
    assertThat(rows.count(JOURNAL_ENTRY)).isEqualTo(2 * PARTS);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isEqualTo(PARTS);
  }

  private List<Throwable> mirrorConcurrently(List<JournalEntryPart> parts) throws Exception {
    var pool = Executors.newFixedThreadPool(OVERLAPPING_RUNS);
    var barrier = new CyclicBarrier(OVERLAPPING_RUNS);
    List<Throwable> errors = new CopyOnWriteArrayList<>();
    List<Future<?>> runs = new ArrayList<>();
    try {
      for (int run = 0; run < OVERLAPPING_RUNS; run++) {
        runs.add(
            pool.submit(
                () -> {
                  try {
                    barrier.await();
                    generalLedger.mirror(ENTITY, SOURCE, parts, MAX_DELETION_SHARE);
                  } catch (Throwable t) {
                    errors.add(t);
                  }
                  return null;
                }));
      }
      for (Future<?> future : runs) {
        future.get(60, SECONDS);
      }
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(10, SECONDS);
    }
    return errors;
  }

  private List<List<JournalEntryVersion>> versionsOf(List<JournalEntryPart> parts) {
    return parts.stream().map(part -> rows.versionsOf(ENTITY, SOURCE, part.sourceKey())).toList();
  }

  private static List<JournalEntryPart> book(String amount) {
    return IntStream.rangeClosed(1, PARTS)
        .mapToObj(
            day -> {
              var date = LocalDate.of(2026, 1, day);
              return new JournalEntryPart(
                  "ARVE:" + day + ":" + date,
                  "ARVE",
                  date,
                  List.of(
                      new JournalEntryLine("100100", new BigDecimal(amount)),
                      new JournalEntryLine("400100", new BigDecimal(amount).negate())),
                  Set.of());
            })
        .toList();
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }
}
