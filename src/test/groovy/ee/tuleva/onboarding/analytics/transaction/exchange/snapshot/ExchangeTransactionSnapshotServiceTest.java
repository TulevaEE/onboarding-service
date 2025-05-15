package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static shadow.org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransaction;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture;
import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
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

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    fixedOffsetTime = OffsetDateTime.now(ClockHolder.clock());
    recordCreationTime = fixedOffsetTime;
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void takeSnapshot_createsSnapshotsFromCurrentTransactions_forWeeklyJobType() {
    // given
    ExchangeTransaction tx1 = ExchangeTransactionFixture.exampleTransaction();
    ExchangeTransaction tx2 = ExchangeTransactionFixture.anotherExampleTransaction();
    List<ExchangeTransaction> currentTransactions = List.of(tx1, tx2);
    when(currentTransactionRepository.findAll()).thenReturn(currentTransactions);

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
            });

    ExchangeTransactionSnapshot snapshot1 =
        savedSnapshots.stream().filter(s -> s.getCode().equals(tx1.getCode())).findFirst().get();
    ExchangeTransactionSnapshot snapshot2 =
        savedSnapshots.stream().filter(s -> s.getCode().equals(tx2.getCode())).findFirst().get();

    assertThat(snapshot1.getSecurityFrom()).isEqualTo(tx1.getSecurityFrom());
    assertThat(snapshot1.getSourceDateCreated()).isEqualTo(tx1.getDateCreated());
    assertThat(snapshot1.getPercentage()).isEqualTo(tx1.getPercentage());

    assertThat(snapshot2.getSecurityFrom()).isEqualTo(tx2.getSecurityFrom());
    assertThat(snapshot2.getSourceDateCreated()).isEqualTo(tx2.getDateCreated());
    assertThat(snapshot2.getPercentage()).isEqualTo(tx2.getPercentage());
  }

  @Test
  void takeSnapshot_createsSnapshotsFromCurrentTransactions_forMonthlyJobType() {
    // given
    ExchangeTransaction tx1 = ExchangeTransactionFixture.exampleTransaction();
    List<ExchangeTransaction> currentTransactions = List.of(tx1);
    when(currentTransactionRepository.findAll()).thenReturn(currentTransactions);

    // when
    snapshotService.takeSnapshot("MONTHLY");

    // then
    verify(snapshotRepository).saveAll(savedSnapshotsCaptor.capture());
    List<ExchangeTransactionSnapshot> savedSnapshots = savedSnapshotsCaptor.getValue();

    assertThat(savedSnapshots).hasSize(1);
    ExchangeTransactionSnapshot snapshot = savedSnapshots.get(0);
    assertThat(snapshot.getSnapshotTakenAt()).isEqualTo(fixedOffsetTime);
    assertThat(snapshot.getCreatedAt()).isEqualTo(recordCreationTime);
    assertThat(snapshot.getCode()).isEqualTo(tx1.getCode());
    assertThat(snapshot.getSourceDateCreated()).isEqualTo(tx1.getDateCreated());
  }

  @Test
  void takeSnapshot_doesNothingWhenNoCurrentTransactionsExist() {
    // given
    when(currentTransactionRepository.findAll()).thenReturn(Collections.emptyList());

    // when
    snapshotService.takeSnapshot("ANY_JOB_TYPE");

    // then
    verify(snapshotRepository, never()).saveAll(any());
  }
}
