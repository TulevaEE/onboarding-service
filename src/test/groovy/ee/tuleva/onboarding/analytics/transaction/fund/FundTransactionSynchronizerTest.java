package ee.tuleva.onboarding.analytics.transaction.fund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.analytics.transaction.generic.SyncResult;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class FundTransactionSynchronizerTest extends FixedClockConfig {

  @Mock private EpisService episService;
  @Mock private FundTransactionRepository repository;
  @Mock private PlatformTransactionManager transactionManager;

  private FundTransactionSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<FundTransaction>> savedEntitiesCaptor;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(new SimpleTransactionStatus());
    synchronizer =
        new FundTransactionSynchronizer(
            episService, repository, new TransactionTemplate(transactionManager));
  }

  private final String fundIsin = "EE1234567890";
  private final LocalDate startDate = LocalDate.of(2025, 4, 1);
  private final LocalDate endDate = LocalDate.of(2025, 4, 15);
  private final int deletedCount = 3;

  private String syncIdentifier() {
    return String.format("ISIN=%s, range=%s to %s", fundIsin, startDate, endDate);
  }

  @Test
  void syncHoldsNoTransactionAcrossTheEpisFetch() throws NoSuchMethodException {
    var sync =
        FundTransactionSynchronizer.class.getMethod(
            "sync", String.class, LocalDate.class, LocalDate.class);

    assertThat(sync.getAnnotation(Transactional.class)).isNull();
  }

  @Test
  void getTransactionTypeNameIsFund() {
    assertThat(synchronizer.getTransactionTypeName()).isEqualTo("fund");
  }

  @Nested
  @DisplayName("When EPIS returns transactions")
  class WhenTransactionsExist {

    private final List<FundTransactionDto> dtos =
        List.of(
            FundTransactionFixture.Dto.newTransactionDto(),
            FundTransactionFixture.Dto.duplicateTransactionDto());

    @Test
    @DisplayName("it deletes existing transactions for the ISIN and date range and saves new ones")
    void sync_deletesAndSaves() {
      // given
      when(episService.getFundTransactions(fundIsin, startDate, endDate)).thenReturn(dtos);
      when(repository.deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate))
          .thenReturn(deletedCount);

      // when
      SyncResult result = synchronizer.sync(fundIsin, startDate, endDate);

      // then
      verify(episService).getFundTransactions(fundIsin, startDate, endDate);
      verify(repository).deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<FundTransaction> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(2);

      FundTransaction firstSaved = savedEntities.get(0);
      assertThat(firstSaved.getIsin()).isEqualTo(fundIsin);
      assertThat(firstSaved.getTransactionDate())
          .isEqualTo(FundTransactionFixture.Dto.NEW_TRANSACTION_DATE);
      assertThat(firstSaved.getPersonalId()).isEqualTo(FundTransactionFixture.Dto.NEW_PERSONAL_ID);
      assertThat(firstSaved.getTransactionType())
          .isEqualTo(FundTransactionFixture.Dto.NEW_TRANSACTION_TYPE);
      assertThat(firstSaved.getAmount()).isEqualTo(FundTransactionFixture.Dto.NEW_AMOUNT);
      assertThat(firstSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      FundTransaction secondSaved = savedEntities.get(1);
      assertThat(secondSaved.getIsin()).isEqualTo(fundIsin);
      assertThat(secondSaved.getTransactionDate())
          .isEqualTo(FundTransactionFixture.Dto.DUPLICATE_TRANSACTION_DATE);
      assertThat(secondSaved.getPersonalId())
          .isEqualTo(FundTransactionFixture.Dto.DUPLICATE_PERSONAL_ID);
      assertThat(secondSaved.getTransactionType())
          .isEqualTo(FundTransactionFixture.Dto.DUPLICATE_TRANSACTION_TYPE);
      assertThat(secondSaved.getAmount()).isEqualTo(FundTransactionFixture.Dto.DUPLICATE_AMOUNT);
      assertThat(secondSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      verifyNoMoreInteractions(repository);
      assertThat(result).isEqualTo(new SyncResult("fund", syncIdentifier(), deletedCount, 2));
    }
  }

  @Nested
  @DisplayName("When EPIS returns no transactions")
  class WhenNoTransactionsExist {

    @Test
    @DisplayName("it does not attempt to delete or save transactions")
    void sync_doesNothing() {
      // given
      when(episService.getFundTransactions(fundIsin, startDate, endDate))
          .thenReturn(Collections.emptyList());

      // when
      SyncResult result = synchronizer.sync(fundIsin, startDate, endDate);

      // then
      verify(episService).getFundTransactions(fundIsin, startDate, endDate);
      verify(repository, never()).deleteByIsinAndTransactionDateBetween(any(), any(), any());
      verify(repository, never()).saveAll(any());
      assertThat(result).isEqualTo(new SyncResult("fund", syncIdentifier(), 0, 0));
    }
  }

  @Nested
  @DisplayName("When EPIS call fails")
  class WhenEpisFails {

    @Test
    @DisplayName("it logs an error and does not delete or save")
    void sync_logsErrorAndAborts() {
      // given
      RuntimeException simulatedException = new RuntimeException("EPIS connection failed");
      when(episService.getFundTransactions(fundIsin, startDate, endDate))
          .thenThrow(simulatedException);

      // when
      SyncResult result = synchronizer.sync(fundIsin, startDate, endDate);

      // then
      verify(episService).getFundTransactions(fundIsin, startDate, endDate);
      verify(repository, never())
          .deleteByIsinAndTransactionDateBetween(
              anyString(), any(LocalDate.class), any(LocalDate.class));
      verify(repository, never()).saveAll(any());
      assertThat(result).isEqualTo(new SyncResult("fund", syncIdentifier(), 0, 0));
    }
  }

  @Nested
  @DisplayName("When deletion fails")
  class WhenDeleteFails {
    private final List<FundTransactionDto> dtos =
        List.of(FundTransactionFixture.Dto.newTransactionDto());

    @Test
    @DisplayName("it logs an error and does not save")
    void sync_logsErrorAndAbortsSave() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database delete failed");
      when(episService.getFundTransactions(fundIsin, startDate, endDate)).thenReturn(dtos);
      when(repository.deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate))
          .thenThrow(simulatedException);

      // when
      SyncResult result = synchronizer.sync(fundIsin, startDate, endDate);

      // then
      verify(episService).getFundTransactions(fundIsin, startDate, endDate);
      verify(repository).deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate);
      verify(repository, never()).saveAll(any());
      assertThat(result).isEqualTo(new SyncResult("fund", syncIdentifier(), 0, 0));
    }
  }

  @Nested
  @DisplayName("When saving fails")
  class WhenSaveFails {
    private final List<FundTransactionDto> dtos =
        List.of(FundTransactionFixture.Dto.newTransactionDto());

    @Test
    @DisplayName("it logs an error after attempting delete and conversion")
    void sync_logsErrorAfterDelete() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database save failed");
      when(episService.getFundTransactions(fundIsin, startDate, endDate)).thenReturn(dtos);
      when(repository.deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate))
          .thenReturn(deletedCount);
      doThrow(simulatedException).when(repository).saveAll(anyList());

      // when
      SyncResult result = synchronizer.sync(fundIsin, startDate, endDate);

      // then
      verify(episService).getFundTransactions(fundIsin, startDate, endDate);
      verify(repository).deleteByIsinAndTransactionDateBetween(fundIsin, startDate, endDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());
      assertThat(savedEntitiesCaptor.getValue()).hasSize(1);
      assertThat(result).isEqualTo(new SyncResult("fund", syncIdentifier(), 0, 0));
    }
  }
}
