package ee.tuleva.onboarding.analytics.fund;

import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.Dto.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class FundTransactionSynchronizerTest {

  private static final String FUND_ISIN = "EE3600019832";
  private static final LocalDate START_DATE = LocalDate.of(2025, 4, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 4, 30);

  @Mock private EpisService episService;

  @Mock private AnalyticsFundTransactionRepository repository;

  @InjectMocks private FundTransactionSynchronizer synchronizer;

  @Captor ArgumentCaptor<List<FundTransaction>> saveCaptor;

  private final LocalDateTime fixedTime = LocalDateTime.now(TestClockHolder.clock);

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void syncTransactions_insertsOnlyNewTransactions_skipsDuplicates() {
    // Given
    FundTransactionDto newDto = newTransactionDto();
    FundTransactionDto duplicateDto = duplicateTransactionDto();

    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenReturn(List.of(newDto, duplicateDto));

    when(repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            eq(NEW_TRANSACTION_DATE),
            eq(NEW_PERSONAL_ID),
            eq(NEW_TRANSACTION_TYPE),
            eq(NEW_AMOUNT),
            eq(NEW_UNIT_AMOUNT)))
        .thenReturn(false);

    when(repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            eq(DUPLICATE_TRANSACTION_DATE),
            eq(DUPLICATE_PERSONAL_ID),
            eq(DUPLICATE_TRANSACTION_TYPE),
            eq(DUPLICATE_AMOUNT),
            eq(DUPLICATE_UNIT_AMOUNT)))
        .thenReturn(true);

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, times(1)).saveAll(saveCaptor.capture());
    List<FundTransaction> savedEntities = saveCaptor.getValue();
    assertThat(savedEntities).hasSize(1);
    assertThat(savedEntities.get(0).getPersonalId()).isEqualTo(NEW_PERSONAL_ID);
    assertThat(savedEntities.get(0).getIsin()).isEqualTo(FUND_ISIN);
    assertThat(savedEntities.get(0).getDateCreated()).isEqualTo(fixedTime);

    verify(repository)
        .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            NEW_TRANSACTION_DATE,
            NEW_PERSONAL_ID,
            NEW_TRANSACTION_TYPE,
            NEW_AMOUNT,
            NEW_UNIT_AMOUNT);
    verify(repository)
        .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            DUPLICATE_TRANSACTION_DATE,
            DUPLICATE_PERSONAL_ID,
            DUPLICATE_TRANSACTION_TYPE,
            DUPLICATE_AMOUNT,
            DUPLICATE_UNIT_AMOUNT);
  }

  @Test
  void syncTransactions_whenEpisServiceReturnsEmptyList_shouldNotSave() {
    // Given
    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenReturn(Collections.emptyList());

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, never()).saveAll(any());
    verify(repository, never())
        .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            any(), any(), any(), any(), any());
  }

  @Test
  void syncTransactions_whenEpisServiceThrowsException_shouldLogErrorAndNotSave() {
    // Given
    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenThrow(new RuntimeException("Simulated EpisService error"));

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, never()).saveAll(any());
    verify(repository, never())
        .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            any(), any(), any(), any(), any());
  }

  @Test
  void syncTransactions_whenAllTransactionsAreDuplicates_shouldSkipAll() {
    // Given
    FundTransactionDto duplicateDto1 = newTransactionDto();
    FundTransactionDto duplicateDto2 = duplicateTransactionDto();

    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenReturn(List.of(duplicateDto1, duplicateDto2));

    when(repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            any(), any(), any(), any(), any()))
        .thenReturn(true);

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, never()).saveAll(any());
    verify(repository, times(2))
        .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            any(), any(), any(), any(), any());
  }

  @Test
  void syncTransactions_whenAllTransactionsAreNew_shouldInsertAll() {
    // Given
    FundTransactionDto newDto1 = newTransactionDto();
    FundTransactionDto newDto2 = duplicateTransactionDto();

    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenReturn(List.of(newDto1, newDto2));

    when(repository.existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            any(), any(), any(), any(), any()))
        .thenReturn(false);

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, times(1)).saveAll(saveCaptor.capture());
    List<FundTransaction> savedEntities = saveCaptor.getValue();
    assertThat(savedEntities).hasSize(2);
    assertThat(savedEntities.get(0).getPersonalId()).isEqualTo(NEW_PERSONAL_ID);
    assertThat(savedEntities.get(0).getDateCreated()).isEqualTo(fixedTime);
    assertThat(savedEntities.get(1).getPersonalId()).isEqualTo(DUPLICATE_PERSONAL_ID);
    assertThat(savedEntities.get(1).getDateCreated()).isEqualTo(fixedTime);

    verify(repository, times(2))
        .existsByTransactionDateAndPersonalIdAndTransactionTypeAndAmountAndUnitAmount(
            any(), any(), any(), any(), any());
  }
}
