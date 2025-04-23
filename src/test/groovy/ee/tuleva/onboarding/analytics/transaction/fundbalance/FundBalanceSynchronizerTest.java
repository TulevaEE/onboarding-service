package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.TransactionFundBalanceDto;
import ee.tuleva.onboarding.time.FixedClockConfig;
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
class FundBalanceSynchronizerTest extends FixedClockConfig {

  @Mock private EpisService episService;
  @Mock private FundBalanceRepository repository;

  @InjectMocks private FundBalanceSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<FundBalance>> savedEntitiesCaptor;

  private final LocalDate syncDate = LocalDate.of(2025, 4, 21);
  private final int deletedCount = 5;

  @Nested
  @DisplayName("When EPIS returns fund balances")
  class WhenBalancesExist {

    private final List<TransactionFundBalanceDto> dtos =
        List.of(
            FundBalanceFixture.dtoBuilder()
                .isin(FundBalanceFixture.ISIN_1)
                .requestDate(syncDate)
                .build(),
            FundBalanceFixture.dtoBuilder()
                .isin(FundBalanceFixture.ISIN_2)
                .requestDate(syncDate)
                .build());

    @Test
    @DisplayName("it deletes existing balances for the date and saves new ones")
    void sync_deletesAndSaves() {
      // given
      when(episService.getFundBalances(syncDate)).thenReturn(dtos);
      when(repository.deleteByRequestDate(syncDate)).thenReturn(deletedCount);

      // when
      synchronizer.sync(syncDate);

      // then
      verify(episService).getFundBalances(syncDate);
      verify(repository).deleteByRequestDate(syncDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<FundBalance> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(2);

      assertThat(savedEntities.get(0).getIsin()).isEqualTo(FundBalanceFixture.ISIN_1);
      assertThat(savedEntities.get(0).getRequestDate()).isEqualTo(syncDate);
      assertThat(savedEntities.get(0).getDateCreated()).isEqualTo(testLocalDateTime);

      assertThat(savedEntities.get(1).getIsin()).isEqualTo(FundBalanceFixture.ISIN_2);
      assertThat(savedEntities.get(1).getRequestDate()).isEqualTo(syncDate);
      assertThat(savedEntities.get(1).getDateCreated()).isEqualTo(testLocalDateTime);

      verifyNoMoreInteractions(repository);
    }
  }

  @Nested
  @DisplayName("When EPIS returns no fund balances")
  class WhenNoBalancesExist {

    @Test
    @DisplayName("it does not attempt to delete or save")
    void sync_doesNothing() {
      // given
      when(episService.getFundBalances(syncDate)).thenReturn(Collections.emptyList());

      // when
      synchronizer.sync(syncDate);

      // then
      verify(episService).getFundBalances(syncDate);
      verify(repository, never()).deleteByRequestDate(any(LocalDate.class));
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
      when(episService.getFundBalances(syncDate)).thenThrow(simulatedException);

      // when
      synchronizer.sync(syncDate);

      // then
      verify(episService).getFundBalances(syncDate);
      verify(repository, never()).deleteByRequestDate(any(LocalDate.class));
      verify(repository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("When deletion fails")
  class WhenDeleteFails {
    private final List<TransactionFundBalanceDto> dtos =
        List.of(FundBalanceFixture.dtoBuilder().requestDate(syncDate).build());

    @Test
    @DisplayName("it logs an error and does not save")
    void sync_logsErrorAndAbortsSave() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database delete failed");
      when(episService.getFundBalances(syncDate)).thenReturn(dtos);
      when(repository.deleteByRequestDate(syncDate)).thenThrow(simulatedException);

      // when
      synchronizer.sync(syncDate);

      // then
      verify(episService).getFundBalances(syncDate);
      verify(repository).deleteByRequestDate(syncDate);
      verify(repository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("When saving fails")
  class WhenSaveFails {
    private final List<TransactionFundBalanceDto> dtos =
        List.of(FundBalanceFixture.dtoBuilder().requestDate(syncDate).build());

    @Test
    @DisplayName("it logs an error after attempting delete and conversion")
    void sync_logsErrorAfterDelete() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database save failed");
      when(episService.getFundBalances(syncDate)).thenReturn(dtos);
      when(repository.deleteByRequestDate(syncDate)).thenReturn(deletedCount);
      doThrow(simulatedException).when(repository).saveAll(anyList());

      // when
      synchronizer.sync(syncDate);

      // then
      verify(episService).getFundBalances(syncDate);
      verify(repository).deleteByRequestDate(syncDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());
      assertThat(savedEntitiesCaptor.getValue()).hasSize(1);
    }
  }
}
