package ee.tuleva.onboarding.analytics.transaction.exchange;

import static ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture.Dto.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
class ExchangeTransactionSynchronizerTest extends FixedClockConfig {

  @Mock private EpisService episService;
  @Mock private ExchangeTransactionRepository repository;

  @InjectMocks private ExchangeTransactionSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<ExchangeTransaction>> savedEntitiesCaptor;

  private final LocalDate reportingDate = LocalDate.of(2025, 1, 15);
  private final Optional<String> securityFrom = Optional.of("SEC_A");
  private final Optional<String> securityTo = Optional.of("SEC_B");
  private final boolean pikFlag = false;
  private final int deletedCount = 5;

  @Nested
  @DisplayName("When EPIS returns transactions")
  class WhenTransactionsExist {

    private final List<ExchangeTransactionDto> dtos =
        List.of(
            ExchangeTransactionFixture.Dto.newTransactionDto(),
            ExchangeTransactionFixture.Dto.duplicateTransactionDto());

    @Test
    @DisplayName("it deletes existing transactions for the reporting date and saves new ones")
    void sync_deletesAndSaves() {
      // given
      when(episService.getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag))
          .thenReturn(dtos);
      when(repository.deleteByReportingDate(reportingDate)).thenReturn(deletedCount);

      // when
      synchronizer.sync(reportingDate, securityFrom, securityTo, pikFlag);

      // then
      verify(episService).getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag);
      verify(repository).deleteByReportingDate(reportingDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());

      List<ExchangeTransaction> savedEntities = savedEntitiesCaptor.getValue();
      assertThat(savedEntities).hasSize(2);

      ExchangeTransaction firstSaved = savedEntities.get(0);
      assertThat(firstSaved.getReportingDate()).isEqualTo(reportingDate);
      assertThat(firstSaved.getSecurityFrom())
          .isEqualTo(ExchangeTransactionFixture.Dto.NEW_SECURITY_FROM);
      assertThat(firstSaved.getSecurityTo())
          .isEqualTo(ExchangeTransactionFixture.Dto.NEW_SECURITY_TO);
      assertThat(firstSaved.getCode()).isEqualTo(ExchangeTransactionFixture.Dto.NEW_CODE);
      assertThat(firstSaved.getPercentage()).isEqualTo(BigDecimal.valueOf(2.5));
      assertThat(firstSaved.getUnitAmount()).isEqualTo(BigDecimal.valueOf(10.0));
      assertThat(firstSaved.getDateCreated()).isEqualTo(testLocalDateTime);

      ExchangeTransaction secondSaved = savedEntities.get(1);
      assertThat(secondSaved.getReportingDate()).isEqualTo(reportingDate);
      assertThat(secondSaved.getSecurityFrom()).isEqualTo(DUPLICATE_SECURITY_FROM);
      assertThat(secondSaved.getSecurityTo()).isEqualTo(DUPLICATE_SECURITY_TO);
      assertThat(secondSaved.getCode()).isEqualTo(DUPLICATE_CODE);
      assertThat(secondSaved.getPercentage()).isEqualTo(BigDecimal.valueOf(5.0));
      assertThat(secondSaved.getUnitAmount()).isEqualTo(BigDecimal.valueOf(50.0));
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
      when(episService.getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag))
          .thenReturn(Collections.emptyList());

      // when
      synchronizer.sync(reportingDate, securityFrom, securityTo, pikFlag);

      // then
      verify(episService).getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag);
      verify(repository, never()).deleteByReportingDate(any());
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
      when(episService.getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag))
          .thenThrow(simulatedException);

      // when
      synchronizer.sync(reportingDate, securityFrom, securityTo, pikFlag);

      // then
      verify(episService).getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag);
      verify(repository, never()).deleteByReportingDate(any(LocalDate.class));
      verify(repository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("When deletion fails")
  class WhenDeleteFails {
    private final List<ExchangeTransactionDto> dtos =
        List.of(ExchangeTransactionFixture.Dto.newTransactionDto());

    @Test
    @DisplayName("it logs an error and does not save")
    void sync_logsErrorAndAbortsSave() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database delete failed");
      when(episService.getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag))
          .thenReturn(dtos);
      when(repository.deleteByReportingDate(reportingDate)).thenThrow(simulatedException);

      // when
      synchronizer.sync(reportingDate, securityFrom, securityTo, pikFlag);

      // then
      verify(episService).getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag);
      verify(repository).deleteByReportingDate(reportingDate);
      verify(repository, never()).saveAll(any());
    }
  }

  @Nested
  @DisplayName("When saving fails")
  class WhenSaveFails {
    private final List<ExchangeTransactionDto> dtos =
        List.of(ExchangeTransactionFixture.Dto.newTransactionDto());

    @Test
    @DisplayName("it logs an error after attempting delete and conversion")
    void sync_logsErrorAfterDelete() {
      // given
      RuntimeException simulatedException = new RuntimeException("Database save failed");
      when(episService.getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag))
          .thenReturn(dtos);
      when(repository.deleteByReportingDate(reportingDate)).thenReturn(deletedCount);
      doThrow(simulatedException).when(repository).saveAll(anyList());

      // when
      synchronizer.sync(reportingDate, securityFrom, securityTo, pikFlag);

      // then
      verify(episService).getExchangeTransactions(reportingDate, securityFrom, securityTo, pikFlag);
      verify(repository).deleteByReportingDate(reportingDate);
      verify(repository).saveAll(savedEntitiesCaptor.capture());
      assertThat(savedEntitiesCaptor.getValue()).hasSize(1);
    }
  }
}
