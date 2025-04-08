package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransaction;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.PensionTransaction;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ThirdPillarTransactionSynchronizerTest {

  @Mock private EpisService episService;

  @Mock private AnalyticsThirdPillarTransactionRepository repository;

  @InjectMocks private ThirdPillarTransactionSynchronizer synchronizer;

  @Captor private ArgumentCaptor<List<AnalyticsThirdPillarTransaction>> transactionListCaptor;

  private static final LocalDate START_DATE = LocalDate.of(2023, 1, 1);
  private static final LocalDate END_DATE = LocalDate.of(2023, 1, 31);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneId.of("UTC"));

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ClockHolder.setClock(FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void syncTransactions_deletesExistingAndSavesNewTransactions() {
    // Given
    PensionTransaction transaction1 = PensionTransactionFixture.johnDoeTransaction();
    PensionTransaction transaction2 = PensionTransactionFixture.janeSmithTransaction();
    List<PensionTransaction> transactionsFromEpis = Arrays.asList(transaction1, transaction2);

    when(episService.getTransactions(START_DATE, END_DATE)).thenReturn(transactionsFromEpis);
    when(repository.deleteByReportingDateBetween(START_DATE, END_DATE)).thenReturn(5);

    // When
    synchronizer.syncTransactions(START_DATE, END_DATE);

    // Then
    verify(repository, times(1)).deleteByReportingDateBetween(START_DATE, END_DATE);
    verify(repository, times(1)).saveAll(transactionListCaptor.capture());

    List<AnalyticsThirdPillarTransaction> savedTransactions = transactionListCaptor.getValue();
    assert savedTransactions.size() == 2;
    assert savedTransactions
        .get(0)
        .getPersonalId()
        .equals(PensionTransactionFixture.JOHN_DOE_PERSONAL_ID);
    assert savedTransactions
        .get(1)
        .getPersonalId()
        .equals(PensionTransactionFixture.JANE_SMITH_PERSONAL_ID);
    assert savedTransactions.get(0).getDateCreated().equals(LocalDateTime.now(FIXED_CLOCK));
    assert savedTransactions.get(1).getDateCreated().equals(LocalDateTime.now(FIXED_CLOCK));
  }

  @Test
  void syncTransactions_whenNoTransactionsRetrieved_doesNotDeleteOrSave() {
    // Given
    when(episService.getTransactions(START_DATE, END_DATE)).thenReturn(Collections.emptyList());

    // When
    synchronizer.syncTransactions(START_DATE, END_DATE);

    // Then
    verify(repository, never())
        .deleteByReportingDateBetween(any(LocalDate.class), any(LocalDate.class));
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void syncTransactions_whenEpisServiceThrowsException_shouldNotDeleteOrSave() {
    // Given
    when(episService.getTransactions(START_DATE, END_DATE))
        .thenThrow(new RuntimeException("EPIS connection failed"));

    // When
    synchronizer.syncTransactions(START_DATE, END_DATE);

    // Then
    verify(repository, never())
        .deleteByReportingDateBetween(any(LocalDate.class), any(LocalDate.class));
    verify(repository, never()).saveAll(anyList());
  }
}
