package ee.tuleva.onboarding.analytics.transaction.unitowner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerDto;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class UnitOwnerSynchronizerTest extends FixedClockConfig {

  @Mock private EpisService episService;
  @Mock private UnitOwnerRepository repository;
  @Mock private PlatformTransactionManager transactionManager;

  private UnitOwnerSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<UnitOwner>> savedEntitiesCaptor;

  private final LocalDate snapshotDate = LocalDate.of(2025, 4, 22);

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(new SimpleTransactionStatus());
    synchronizer =
        new UnitOwnerSynchronizer(
            episService, repository, new TransactionTemplate(transactionManager));
  }

  @Test
  void syncHoldsNoTransactionAcrossTheEpisFetch() throws NoSuchMethodException {
    var sync = UnitOwnerSynchronizer.class.getMethod("sync", LocalDate.class);

    assertThat(sync.getAnnotation(Transactional.class)).isNull();
  }

  @Test
  void fetchRunsOutsideTheDatabaseTransaction() {
    given(episService.getUnitOwners())
        .willReturn(List.of(UnitOwnerFixture.dtoBuilder(UnitOwnerFixture.PERSON_ID_1).build()));

    synchronizer.sync(snapshotDate);

    InOrder inOrder = inOrder(episService, transactionManager, repository);
    inOrder.verify(episService).getUnitOwners();
    inOrder.verify(transactionManager).getTransaction(any());
    inOrder.verify(repository).saveAll(anyList());
  }

  @Test
  void emptyFetchNeverStartsATransaction() {
    given(episService.getUnitOwners()).willReturn(List.of());

    synchronizer.sync(snapshotDate);

    verifyNoInteractions(transactionManager);
  }

  @Nested
  @DisplayName("When EPIS returns unit owners")
  class WhenOwnersExist {

    private final List<UnitOwnerDto> dtosWithBalances =
        List.of(
            UnitOwnerFixture.dtoBuilder(UnitOwnerFixture.PERSON_ID_1).build(),
            UnitOwnerFixture.dtoBuilder(UnitOwnerFixture.PERSON_ID_2).build());

    @Test
    @DisplayName("it saves new snapshots with balances and does not delete")
    void sync_savesSnapshotsWithBalances() {
      // given
      when(episService.getUnitOwners()).thenReturn(dtosWithBalances);

      // when
      synchronizer.sync(snapshotDate);

      // then
      verify(episService).getUnitOwners();
      verify(repository, never()).deleteAll();
      verify(repository, never()).deleteAll(anyList());
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<UnitOwner> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(2);

      UnitOwner firstSaved = savedEntities.get(0);
      assertThat(firstSaved.getPersonalId()).isEqualTo(UnitOwnerFixture.PERSON_ID_1);
      assertThat(firstSaved.getSnapshotDate()).isEqualTo(snapshotDate);
      assertThat(firstSaved.getLastName()).isEqualTo("Maasikas");
      assertThat(firstSaved.getP2choice()).isEqualTo("APPLICATION");
      assertThat(firstSaved.getP3identifier()).isEqualTo("ID_CARD");
      assertThat(firstSaved.getBalances()).hasSize(2);
      assertThat(firstSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      UnitOwner secondSaved = savedEntities.get(1);
      assertThat(secondSaved.getPersonalId()).isEqualTo(UnitOwnerFixture.PERSON_ID_2);
      assertThat(secondSaved.getSnapshotDate()).isEqualTo(snapshotDate);
      assertThat(secondSaved.getBalances()).hasSize(2);
      assertThat(secondSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      verifyNoMoreInteractions(episService, repository);
    }

    @Test
    @DisplayName("it saves snapshot with empty balances when DTO balances list is null")
    void sync_savesSnapshotWithEmptyBalances_whenDtoBalancesIsNull() {
      // given
      UnitOwnerDto dtoWithoutBalances =
          UnitOwnerFixture.dtoBuilder(UnitOwnerFixture.PERSON_ID_3).balances(null).build();
      when(episService.getUnitOwners()).thenReturn(List.of(dtoWithoutBalances));

      // when
      synchronizer.sync(snapshotDate);

      // then
      verify(episService).getUnitOwners();
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<UnitOwner> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(1);

      UnitOwner savedEntity = savedEntities.get(0);
      assertThat(savedEntity.getPersonalId()).isEqualTo(UnitOwnerFixture.PERSON_ID_3);
      assertThat(savedEntity.getSnapshotDate()).isEqualTo(snapshotDate);
      assertThat(savedEntity.getBalances()).isNotNull().isEmpty();
      assertThat(savedEntity.getDateCreated()).isEqualTo(testLocalDateTime);

      verifyNoMoreInteractions(episService, repository);
    }

    @Test
    @DisplayName("it saves snapshot with empty balances when DTO balances list is empty")
    void sync_savesSnapshotWithEmptyBalances_whenDtoBalancesIsEmpty() {
      // given
      UnitOwnerDto dtoWithEmptyBalances =
          UnitOwnerFixture.dtoBuilder(UnitOwnerFixture.PERSON_ID_4)
              .balances(Collections.emptyList())
              .build();
      when(episService.getUnitOwners()).thenReturn(List.of(dtoWithEmptyBalances));

      // when
      synchronizer.sync(snapshotDate);

      // then
      verify(episService).getUnitOwners();
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<UnitOwner> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(1);

      UnitOwner savedEntity = savedEntities.get(0);
      assertThat(savedEntity.getPersonalId()).isEqualTo(UnitOwnerFixture.PERSON_ID_4);
      assertThat(savedEntity.getSnapshotDate()).isEqualTo(snapshotDate);
      assertThat(savedEntity.getBalances()).isNotNull().isEmpty(); // *** ASSERTION ***
      assertThat(savedEntity.getDateCreated()).isEqualTo(testLocalDateTime);

      verifyNoMoreInteractions(episService, repository);
    }
  }

  @Nested
  @DisplayName("When EPIS returns no unit owners")
  class WhenNoOwnersExist {

    @Test
    @DisplayName("it does not attempt to save")
    void sync_doesNothing() {
      // given
      when(episService.getUnitOwners()).thenReturn(Collections.emptyList());

      // when
      synchronizer.sync(snapshotDate);

      // then
      verify(episService).getUnitOwners();
      verify(repository, never()).saveAll(any());
      verifyNoMoreInteractions(episService, repository);
    }
  }

  @Nested
  @DisplayName("When EPIS call fails")
  class WhenEpisFails {

    @Test
    @DisplayName("it logs an error and does not save")
    void sync_logsErrorAndAborts() {
      // given
      RuntimeException simulatedException = new RuntimeException("EPIS connection failed");
      when(episService.getUnitOwners()).thenThrow(simulatedException);

      // when
      synchronizer.sync(snapshotDate);

      // then
      verify(episService).getUnitOwners();
      verify(repository, never()).saveAll(any());
      verifyNoMoreInteractions(episService, repository);
    }
  }

  @Nested
  @DisplayName("When saving fails")
  class WhenSaveFails {
    private final List<UnitOwnerDto> dtos =
        List.of(UnitOwnerFixture.dtoBuilder(UnitOwnerFixture.PERSON_ID_1).build());

    @Test
    @DisplayName("it logs an error after attempting conversion")
    void sync_logsErrorAfterConversion() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database save failed");
      when(episService.getUnitOwners()).thenReturn(dtos);
      doThrow(simulatedException).when(repository).saveAll(anyList());

      // when
      synchronizer.sync(snapshotDate);

      // then
      verify(episService).getUnitOwners();
      verify(repository).saveAll(savedEntitiesCaptor.capture());
      assertThat(savedEntitiesCaptor.getValue()).hasSize(1);

      verifyNoMoreInteractions(episService);
      verify(repository).saveAll(anyList());
      verifyNoMoreInteractions(repository);
    }
  }
}
