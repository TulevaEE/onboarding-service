package ee.tuleva.onboarding.analytics.thirdpillar.synchronization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.PensionTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ThirdPillarTransactionSynchronizerTest {

  @Mock private EpisService episService;

  @Mock private AnalyticsThirdPillarTransactionRepository repository;

  @InjectMocks private ThirdPillarTransactionSynchronizer synchronizer;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void syncTransactions_savesNonDuplicateTransactions() {
    PensionTransaction newTransaction = PensionTransactionFixture.johnDoeTransaction();
    PensionTransaction duplicateTransaction = PensionTransactionFixture.janeSmithTransaction();

    when(episService.getTransactions(any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(Arrays.asList(newTransaction, duplicateTransaction));

    when(repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                any(LocalDate.class),
                eq(PensionTransactionFixture.JOHN_DOE_PERSONAL_ID),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class)))
        .thenReturn(false);

    when(repository
            .existsByReportingDateAndPersonalIdAndTransactionTypeAndTransactionValueAndShareAmount(
                any(LocalDate.class),
                eq(PensionTransactionFixture.JANE_SMITH_PERSONAL_ID),
                any(String.class),
                any(BigDecimal.class),
                any(BigDecimal.class)))
        .thenReturn(true);

    synchronizer.syncTransactions(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31));

    verify(repository, times(1)).save(any());
    verify(repository, times(0))
        .save(
            argThat(
                entity ->
                    entity
                        .getPersonalId()
                        .equals(PensionTransactionFixture.JANE_SMITH_PERSONAL_ID)));
  }

  @Test
  void syncTransactions_whenEpisServiceThrowsException_shouldNotSaveTransactions() {
    LocalDate startDate = LocalDate.of(2023, 2, 1);
    LocalDate endDate = LocalDate.of(2023, 2, 28);

    when(episService.getTransactions(any(LocalDate.class), any(LocalDate.class)))
        .thenThrow(new RuntimeException("Test exception"));

    synchronizer.syncTransactions(startDate, endDate);

    verify(repository, times(0)).save(any());
  }
}
