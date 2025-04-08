package ee.tuleva.onboarding.analytics.fund;

import static ee.tuleva.onboarding.analytics.fund.FundTransactionFixture.Dto.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundTransactionSynchronizerTest {

  private static final String FUND_ISIN = "EE3600019832";
  private static final LocalDate START_DATE = LocalDate.of(2025, 4, 1);
  private static final LocalDate END_DATE = LocalDate.of(2025, 4, 30);

  @Mock private EpisService episService;
  @Mock private FundTransactionRepository repository;

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
  void syncTransactions_deletesExistingInRangeAndInsertsAllFetched() {
    // Given
    FundTransactionDto tx1 = newTransactionDto();
    FundTransactionDto tx2 = duplicateTransactionDto();
    List<FundTransactionDto> fetchedDtos = List.of(tx1, tx2);
    int deleteCount = 5;

    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE)).thenReturn(fetchedDtos);
    when(repository.deleteByIsinAndTransactionDateBetween(FUND_ISIN, START_DATE, END_DATE))
        .thenReturn(deleteCount);

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    InOrder inOrder = inOrder(repository);

    inOrder
        .verify(repository)
        .deleteByIsinAndTransactionDateBetween(FUND_ISIN, START_DATE, END_DATE);

    inOrder.verify(repository).saveAll(saveCaptor.capture());

    verifyNoMoreInteractions(repository);

    List<FundTransaction> savedEntities = saveCaptor.getValue();
    assertThat(savedEntities).hasSize(fetchedDtos.size());

    assertThat(savedEntities.get(0).getPersonalId()).isEqualTo(tx1.getPersonId());
    assertThat(savedEntities.get(0).getIsin()).isEqualTo(FUND_ISIN);
    assertThat(savedEntities.get(0).getTransactionDate()).isEqualTo(tx1.getDate());
    assertThat(savedEntities.get(0).getDateCreated()).isEqualTo(fixedTime);

    assertThat(savedEntities.get(1).getPersonalId()).isEqualTo(tx2.getPersonId());
  }

  @Test
  void syncTransactions_whenEpisServiceReturnsEmptyList_shouldNotDeleteOrSave() {
    // Given
    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenReturn(Collections.emptyList());

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, never()).deleteByIsinAndTransactionDateBetween(any(), any(), any());
    verify(repository, never()).saveAll(any());
    verifyNoMoreInteractions(repository);
  }

  @Test
  void syncTransactions_whenEpisServiceThrowsException_shouldLogErrorAndDeleteAndSaveNotCalled() {
    // Given
    when(episService.getFundTransactions(FUND_ISIN, START_DATE, END_DATE))
        .thenThrow(new RuntimeException("Simulated EpisService error"));

    // When
    synchronizer.syncTransactions(FUND_ISIN, START_DATE, END_DATE);

    // Then
    verify(repository, never()).deleteByIsinAndTransactionDateBetween(any(), any(), any());
    verify(repository, never()).saveAll(any());
    verifyNoMoreInteractions(repository);
  }
}
