package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransaction;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ExchangeTransactionSnapshotServiceIntegrationTest {

  @Autowired private ExchangeTransactionSnapshotService snapshotService;
  @Autowired private ExchangeTransactionRepository currentTransactionRepository;
  @Autowired private ExchangeTransactionSnapshotRepository snapshotRepository;

  private LocalDateTime fixedTestTime;
  private LocalDateTime recordCreationTime;
  private LocalDate today;
  private LocalDate yesterday;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    fixedTestTime = LocalDateTime.now(ClockHolder.clock());
    recordCreationTime = fixedTestTime;
    today = LocalDate.now(ClockHolder.clock());
    yesterday = today.minusDays(1);

    snapshotRepository.deleteAllInBatch();
    currentTransactionRepository.deleteAllInBatch();
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void takeSnapshot_savesSnapshotsOnlyForLatestReportingDate() {
    // given
    ExchangeTransaction tx1Latest =
        ExchangeTransactionFixture.exampleTransactionBuilder()
            .reportingDate(today)
            .code("LATEST_1")
            .build();
    ExchangeTransaction tx2Latest =
        ExchangeTransactionFixture.anotherExampleTransactionBuilder()
            .reportingDate(today)
            .code("LATEST_2")
            .build();
    ExchangeTransaction txOld =
        ExchangeTransactionFixture.exampleTransactionBuilder()
            .reportingDate(yesterday)
            .code("OLD_1")
            .build();
    ExchangeTransaction txVeryOld =
        ExchangeTransactionFixture.anotherExampleTransactionBuilder()
            .reportingDate(yesterday.minusDays(5))
            .code("VERY_OLD_1")
            .build();

    currentTransactionRepository.saveAll(List.of(txOld, tx1Latest, txVeryOld, tx2Latest));
    assertThat(currentTransactionRepository.count()).isEqualTo(4);
    assertThat(snapshotRepository.count()).isEqualTo(0);

    // when
    snapshotService.takeSnapshot("INTEGRATION_TEST_JOB_LATEST_ONLY");

    // then
    assertThat(snapshotRepository.count()).isEqualTo(2);
    List<ExchangeTransactionSnapshot> snapshots = snapshotRepository.findAll();

    assertThat(snapshots)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.getSnapshotTakenAt()).isEqualTo(fixedTestTime);
              assertThat(snapshot.getCreatedAt()).isEqualTo(recordCreationTime);
              assertThat(snapshot.getReportingDate()).isEqualTo(today);
            });

    ExchangeTransactionSnapshot snapshotForTx1Latest =
        snapshots.stream()
            .filter(s -> s.getCode().equals(tx1Latest.getCode()))
            .findFirst()
            .orElse(null);
    assertThat(snapshotForTx1Latest).isNotNull();
    assertSnapshotMatchesTransaction(snapshotForTx1Latest, tx1Latest);

    ExchangeTransactionSnapshot snapshotForTx2Latest =
        snapshots.stream()
            .filter(s -> s.getCode().equals(tx2Latest.getCode()))
            .findFirst()
            .orElse(null);
    assertThat(snapshotForTx2Latest).isNotNull();
    assertSnapshotMatchesTransaction(snapshotForTx2Latest, tx2Latest);

    assertThat(snapshots.stream().map(ExchangeTransactionSnapshot::getCode))
        .doesNotContain(txOld.getCode(), txVeryOld.getCode());
  }

  @Test
  void takeSnapshot_savesAllWhenAllHaveLatestReportingDate() {
    // given
    ExchangeTransaction tx1 =
        ExchangeTransactionFixture.exampleTransactionBuilder().reportingDate(today).build();
    ExchangeTransaction tx2 =
        ExchangeTransactionFixture.anotherExampleTransactionBuilder().reportingDate(today).build();
    currentTransactionRepository.saveAll(List.of(tx1, tx2));
    assertThat(currentTransactionRepository.count()).isEqualTo(2);
    assertThat(snapshotRepository.count()).isEqualTo(0);

    // when
    snapshotService.takeSnapshot("INTEGRATION_TEST_ALL_LATEST");

    // then
    assertThat(snapshotRepository.count()).isEqualTo(2);
    List<ExchangeTransactionSnapshot> snapshots = snapshotRepository.findAll();

    ExchangeTransactionSnapshot snapshotForTx1 =
        snapshots.stream().filter(s -> s.getCode().equals(tx1.getCode())).findFirst().orElse(null);
    assertThat(snapshotForTx1).isNotNull();
    assertSnapshotMatchesTransaction(snapshotForTx1, tx1);

    ExchangeTransactionSnapshot snapshotForTx2 =
        snapshots.stream().filter(s -> s.getCode().equals(tx2.getCode())).findFirst().orElse(null);
    assertThat(snapshotForTx2).isNotNull();
    assertSnapshotMatchesTransaction(snapshotForTx2, tx2);
  }

  @Test
  void takeSnapshot_handlesEmptyCurrentTransactions() {
    // given
    assertThat(currentTransactionRepository.count()).isEqualTo(0);
    assertThat(snapshotRepository.count()).isEqualTo(0);

    // when
    snapshotService.takeSnapshot("EMPTY_TEST_JOB");

    // then
    assertThat(snapshotRepository.count()).isEqualTo(0);
  }

  private void assertSnapshotMatchesTransaction(
      ExchangeTransactionSnapshot snapshot, ExchangeTransaction transaction) {
    assertThat(snapshot.getReportingDate()).isEqualTo(transaction.getReportingDate());
    assertThat(snapshot.getSecurityFrom()).isEqualTo(transaction.getSecurityFrom());
    assertThat(snapshot.getSecurityTo()).isEqualTo(transaction.getSecurityTo());
    assertThat(snapshot.getFundManagerFrom()).isEqualTo(transaction.getFundManagerFrom());
    assertThat(snapshot.getFundManagerTo()).isEqualTo(transaction.getFundManagerTo());
    assertThat(snapshot.getCode()).isEqualTo(transaction.getCode());
    assertThat(snapshot.getFirstName()).isEqualTo(transaction.getFirstName());
    assertThat(snapshot.getName()).isEqualTo(transaction.getName());
    assertThat(snapshot.getPercentage()).isEqualByComparingTo(transaction.getPercentage());
    assertThat(snapshot.getUnitAmount()).isEqualByComparingTo(transaction.getUnitAmount());
    assertThat(snapshot.getSourceDateCreated()).isEqualTo(transaction.getDateCreated());
  }
}
