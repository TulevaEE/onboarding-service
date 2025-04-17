package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ThirdPillarTransactionDto;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThirdPillarTransactionSynchronizerTest extends FixedClockConfig {

  @Mock private EpisService episService;
  @Mock private AnalyticsThirdPillarTransactionRepository repository;

  @InjectMocks private ThirdPillarTransactionSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<AnalyticsThirdPillarTransaction>> savedEntitiesCaptor;

  private final LocalDate startDate = LocalDate.of(2023, 1, 1);
  private final LocalDate endDate = LocalDate.of(2023, 1, 31);
  private final int deletedCount = 10;

  @Nested
  @DisplayName("When EPIS returns transactions")
  class WhenTransactionsExist {

    private final List<ThirdPillarTransactionDto> dtos =
        List.of(
            ThirdPillarTransactionFixture.johnDoeTransaction(),
            ThirdPillarTransactionFixture.janeSmithTransaction());

    @Test
    @DisplayName("it deletes existing transactions for the date range and saves new ones")
    void sync_deletesAndSaves() {
      // given
      when(episService.getTransactions(startDate, endDate)).thenReturn(dtos);
      when(repository.deleteByReportingDateBetween(startDate, endDate)).thenReturn(deletedCount);

      // when
      synchronizer.sync(startDate, endDate);

      // then
      verify(episService).getTransactions(startDate, endDate);
      verify(repository).deleteByReportingDateBetween(startDate, endDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<AnalyticsThirdPillarTransaction> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(2);

      AnalyticsThirdPillarTransaction firstSaved = savedEntities.get(0);
      assertThat(firstSaved.getReportingDate()).isEqualTo(LocalDate.of(2023, 1, 15));
      assertThat(firstSaved.getPersonalId())
          .isEqualTo(ThirdPillarTransactionFixture.JOHN_DOE_PERSONAL_ID);
      assertThat(firstSaved.getFullName()).isEqualTo("John Doe");
      assertThat(firstSaved.getTransactionType()).isEqualTo("SUBSCRIPTION");
      assertThat(firstSaved.getTransactionValue()).isEqualTo(BigDecimal.valueOf(12.50));
      assertThat(firstSaved.getFundManager()).isEqualTo("Tuleva");
      assertThat(firstSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      AnalyticsThirdPillarTransaction secondSaved = savedEntities.get(1);
      assertThat(secondSaved.getReportingDate()).isEqualTo(LocalDate.of(2023, 1, 15));
      assertThat(secondSaved.getPersonalId())
          .isEqualTo(ThirdPillarTransactionFixture.JANE_SMITH_PERSONAL_ID);
      assertThat(secondSaved.getFullName()).isEqualTo("Jane Smith");
      assertThat(secondSaved.getTransactionType()).isEqualTo("SUBSCRIPTION");
      assertThat(secondSaved.getTransactionValue()).isEqualTo(BigDecimal.valueOf(10.00));
      assertThat(secondSaved.getCounterpartyBank()).isEqualTo("SEB");
      assertThat(secondSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      verifyNoMoreInteractions(repository);
    }
  }

  @Nested
  @DisplayName("When EPIS returns no transactions")
  class WhenNoTransactionsExist {

    @Test
    @DisplayName("it does not attempt to delete or save transactions")
    void sync_doesNothing() {
      // given
      when(episService.getTransactions(startDate, endDate)).thenReturn(Collections.emptyList());

      // when
      synchronizer.sync(startDate, endDate);

      // then
      verify(episService).getTransactions(startDate, endDate);
      verify(repository, never()).deleteByReportingDateBetween(any(), any());
      verify(repository, never()).saveAll(any());
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
      when(episService.getTransactions(startDate, endDate)).thenThrow(simulatedException);

      // when
      synchronizer.sync(startDate, endDate);

      // then
      verify(episService).getTransactions(startDate, endDate);
      verify(repository, never())
          .deleteByReportingDateBetween(any(LocalDate.class), any(LocalDate.class));
      verify(repository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("When deletion fails")
  class WhenDeleteFails {
    private final List<ThirdPillarTransactionDto> dtos =
        List.of(ThirdPillarTransactionFixture.johnDoeTransaction());

    @Test
    @DisplayName("it logs an error and does not save")
    void sync_logsErrorAndAbortsSave() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database delete failed");
      when(episService.getTransactions(startDate, endDate)).thenReturn(dtos);
      when(repository.deleteByReportingDateBetween(startDate, endDate))
          .thenThrow(simulatedException);

      // when
      synchronizer.sync(startDate, endDate);

      // then
      verify(episService).getTransactions(startDate, endDate);
      verify(repository).deleteByReportingDateBetween(startDate, endDate);
      verify(repository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("When saving fails")
  class WhenSaveFails {
    private final List<ThirdPillarTransactionDto> dtos =
        List.of(ThirdPillarTransactionFixture.johnDoeTransaction());

    @Test
    @DisplayName("it logs an error after attempting delete and conversion")
    void sync_logsErrorAfterDelete() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database save failed");
      when(episService.getTransactions(startDate, endDate)).thenReturn(dtos);
      when(repository.deleteByReportingDateBetween(startDate, endDate)).thenReturn(deletedCount);
      doThrow(simulatedException).when(repository).saveAll(anyList());

      // when
      synchronizer.sync(startDate, endDate);

      // then
      verify(episService).getTransactions(startDate, endDate);
      verify(repository).deleteByReportingDateBetween(startDate, endDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());
      assertThat(savedEntitiesCaptor.getValue()).hasSize(1);
    }
  }
}
