package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static shadow.org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransaction;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeTransactionSnapshotServiceTest {

  @Mock private ExchangeTransactionRepository currentTransactionRepository;
  @Mock private ExchangeTransactionSnapshotRepository snapshotRepository;

  @InjectMocks private ExchangeTransactionSnapshotService snapshotService;

  @Captor private ArgumentCaptor<List<ExchangeTransactionSnapshot>> savedSnapshotsCaptor;

  private OffsetDateTime fixedOffsetTime;
  private OffsetDateTime recordCreationTime;
  private LocalDate today;
  private LocalDate yesterday;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    fixedOffsetTime = OffsetDateTime.now(ClockHolder.clock());
    recordCreationTime = fixedOffsetTime;
    today = LocalDate.now(ClockHolder.clock());
    yesterday = today.minusDays(1);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void takeSnapshot_createsSnapshotsOnlyForLatestReportingDate_forWeeklyJobType() {
    // given
    ExchangeTransaction tx1Latest =
        ExchangeTransactionFixture.exampleTransactionBuilder().reportingDate(today).build();
    ExchangeTransaction tx2Latest =
        ExchangeTransactionFixture.anotherExampleTransactionBuilder().reportingDate(today).build();
    ExchangeTransaction txOld =
        ExchangeTransactionFixture.exampleTransactionBuilder()
            .code("OLD_CODE")
            .reportingDate(yesterday)
            .build();

    when(currentTransactionRepository.findTopByOrderByReportingDateDesc())
        .thenReturn(Optional.of(tx1Latest));
    when(currentTransactionRepository.findByReportingDate(today))
        .thenReturn(List.of(tx1Latest, tx2Latest));

    // when
    snapshotService.takeSnapshot("WEEKLY");

    // then
    verify(snapshotRepository).saveAll(savedSnapshotsCaptor.capture());
    List<ExchangeTransactionSnapshot> savedSnapshots = savedSnapshotsCaptor.getValue();

    assertThat(savedSnapshots).hasSize(2);
    assertThat(savedSnapshots)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.getSnapshotTakenAt()).isEqualTo(fixedOffsetTime);
              assertThat(snapshot.getCreatedAt()).isEqualTo(recordCreationTime);
              assertThat(snapshot.getReportingDate()).isEqualTo(today);
            });

    assertThat(savedSnapshots.stream().map(ExchangeTransactionSnapshot::getCode))
        .containsExactlyInAnyOrder(tx1Latest.getCode(), tx2Latest.getCode());
  }

  @Test
  void takeSnapshot_createsSnapshotsOnlyForLatestReportingDate_forMonthlyJobType() {
    // given
    ExchangeTransaction tx1Latest =
        ExchangeTransactionFixture.exampleTransactionBuilder().reportingDate(today).build();

    when(currentTransactionRepository.findTopByOrderByReportingDateDesc())
        .thenReturn(Optional.of(tx1Latest));
    when(currentTransactionRepository.findByReportingDate(today)).thenReturn(List.of(tx1Latest));

    // when
    snapshotService.takeSnapshot("MONTHLY");

    // then
    verify(snapshotRepository).saveAll(savedSnapshotsCaptor.capture());
    List<ExchangeTransactionSnapshot> savedSnapshots = savedSnapshotsCaptor.getValue();

    assertThat(savedSnapshots).hasSize(1);
    ExchangeTransactionSnapshot snapshot = savedSnapshots.getFirst();
    assertThat(snapshot.getSnapshotTakenAt()).isEqualTo(fixedOffsetTime);
    assertThat(snapshot.getCreatedAt()).isEqualTo(recordCreationTime);
    assertThat(snapshot.getReportingDate()).isEqualTo(today);
    assertThat(snapshot.getCode()).isEqualTo(tx1Latest.getCode());
    assertThat(snapshot.getSourceDateCreated()).isEqualTo(tx1Latest.getDateCreated());
  }

  @Test
  void takeSnapshot_doesNothingWhenNoCurrentTransactionsExist() {
    // given
    when(currentTransactionRepository.findTopByOrderByReportingDateDesc())
        .thenReturn(Optional.empty());

    // when
    snapshotService.takeSnapshot("ANY_JOB_TYPE");

    // then
    verify(currentTransactionRepository, never()).findByReportingDate(any());
    verify(snapshotRepository, never()).saveAll(any());
  }

  @Test
  void takeSnapshot_doesNothingWhenNoTransactionsFoundForLatestDate() {
    // given
    ExchangeTransaction txWithLatestDate =
        ExchangeTransactionFixture.exampleTransactionBuilder().reportingDate(today).build();
    when(currentTransactionRepository.findTopByOrderByReportingDateDesc())
        .thenReturn(Optional.of(txWithLatestDate));
    when(currentTransactionRepository.findByReportingDate(today))
        .thenReturn(Collections.emptyList());

    // when
    snapshotService.takeSnapshot("NO_TRANSACTIONS_FOR_LATEST_DATE_JOB");

    // then
    verify(snapshotRepository, never()).saveAll(any());
  }

  @Test
  void takeSnapshot_whenAllTransactionsHaveSameReportingDate() {
    // given
    ExchangeTransaction tx1 =
        ExchangeTransactionFixture.exampleTransactionBuilder().reportingDate(today).build();
    ExchangeTransaction tx2 =
        ExchangeTransactionFixture.anotherExampleTransactionBuilder().reportingDate(today).build();

    when(currentTransactionRepository.findTopByOrderByReportingDateDesc())
        .thenReturn(Optional.of(tx1));
    when(currentTransactionRepository.findByReportingDate(today)).thenReturn(List.of(tx1, tx2));

    // when
    snapshotService.takeSnapshot("SAME_DATE_JOB");

    // then
    verify(snapshotRepository).saveAll(savedSnapshotsCaptor.capture());
    List<ExchangeTransactionSnapshot> savedSnapshots = savedSnapshotsCaptor.getValue();

    assertThat(savedSnapshots).hasSize(2);
    assertThat(savedSnapshots.stream().map(ExchangeTransactionSnapshot::getCode))
        .containsExactlyInAnyOrder(tx1.getCode(), tx2.getCode());
  }
}
