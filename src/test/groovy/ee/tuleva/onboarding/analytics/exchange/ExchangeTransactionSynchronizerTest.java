package ee.tuleva.onboarding.analytics.exchange;

import static ee.tuleva.onboarding.analytics.exchange.ExchangeTransactionFixture.Dto.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeTransactionSynchronizerTest {

  @Mock private EpisService episService;

  @Mock private ExchangeTransactionRepository repository;

  @InjectMocks private ExchangeTransactionSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<ExchangeTransaction>> transactionListCaptor;

  private static final LocalDate REPORTING_DATE = LocalDate.of(2025, 1, 1);

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void syncTransactions_deletesExistingAndInsertsAllRetrievedTransactions() {
    LocalDateTime expectedCreationTime = LocalDateTime.now(TestClockHolder.clock);
    ExchangeTransactionDto dto1 = newTransactionDto();
    ExchangeTransactionDto dto2 = duplicateTransactionDto();

    when(episService.getExchangeTransactions(
            eq(REPORTING_DATE), any(Optional.class), any(Optional.class), eq(false)))
        .thenReturn(List.of(dto1, dto2));
    when(repository.deleteByReportingDate(REPORTING_DATE)).thenReturn(5);

    synchronizer.syncTransactions(REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    verify(repository, times(1)).deleteByReportingDate(REPORTING_DATE);
    verify(repository, times(1)).saveAll(transactionListCaptor.capture());

    List<ExchangeTransaction> savedTransactions = transactionListCaptor.getValue();
    assertThat(savedTransactions).hasSize(2);

    assertThat(savedTransactions)
        .anySatisfy(
            entity -> {
              assertThat(entity.getReportingDate()).isEqualTo(REPORTING_DATE);
              assertThat(entity.getSecurityFrom()).isEqualTo(NEW_SECURITY_FROM);
              assertThat(entity.getSecurityTo()).isEqualTo(NEW_SECURITY_TO);
              assertThat(entity.getCode()).isEqualTo(NEW_CODE);
              assertThat(entity.getDateCreated()).isEqualTo(expectedCreationTime);
            });

    assertThat(savedTransactions)
        .anySatisfy(
            entity -> {
              assertThat(entity.getReportingDate()).isEqualTo(REPORTING_DATE);
              assertThat(entity.getSecurityFrom()).isEqualTo(DUPLICATE_SECURITY_FROM);
              assertThat(entity.getSecurityTo()).isEqualTo(DUPLICATE_SECURITY_TO);
              assertThat(entity.getCode()).isEqualTo(DUPLICATE_CODE);
              assertThat(entity.getDateCreated()).isEqualTo(expectedCreationTime);
            });
  }

  @Test
  void syncTransactions_whenNoTransactionsRetrieved_doesNotDeleteOrSave() {
    when(episService.getExchangeTransactions(
            eq(REPORTING_DATE), any(Optional.class), any(Optional.class), eq(false)))
        .thenReturn(Collections.emptyList());

    synchronizer.syncTransactions(REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    verify(repository, never()).deleteByReportingDate(any(LocalDate.class));
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void syncTransactions_whenEpisServiceThrowsException_shouldLogErrorAndNotDeleteOrSave() {
    when(episService.getExchangeTransactions(
            eq(REPORTING_DATE), any(Optional.class), any(Optional.class), eq(false)))
        .thenThrow(new RuntimeException("Simulated EpisService error"));

    synchronizer.syncTransactions(REPORTING_DATE, Optional.empty(), Optional.empty(), false);

    verify(repository, never()).deleteByReportingDate(any(LocalDate.class));
    verify(repository, never()).saveAll(anyList());
  }
}
