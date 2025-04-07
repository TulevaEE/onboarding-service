package ee.tuleva.onboarding.analytics.exchange;

import static ee.tuleva.onboarding.analytics.exchange.ExchangeTransactionFixture.Dto.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    LocalDate reportingDate = LocalDate.of(2025, 1, 1);
    LocalDateTime expectedCreationTime = LocalDateTime.now(TestClockHolder.clock);

    ExchangeTransactionDto newDto = newTransactionDto();
    ExchangeTransactionDto duplicateDto = duplicateTransactionDto();

    when(episService.getExchangeTransactions(
            eq(reportingDate), any(Optional.class), any(Optional.class), eq(false)))
        .thenReturn(List.of(newDto, duplicateDto));

    when(repository
            .existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
                eq(reportingDate),
                eq(NEW_SECURITY_FROM),
                eq(NEW_SECURITY_TO),
                eq(NEW_CODE),
                eq(BigDecimal.valueOf(10.0)),
                eq(BigDecimal.valueOf(2.5))))
        .thenReturn(false);

    when(repository
            .existsByReportingDateAndSecurityFromAndSecurityToAndCodeAndUnitAmountAndPercentage(
                eq(reportingDate),
                eq(DUPLICATE_SECURITY_FROM),
                eq(DUPLICATE_SECURITY_TO),
                eq(DUPLICATE_CODE),
                eq(BigDecimal.valueOf(50.0)),
                eq(BigDecimal.valueOf(5.0))))
        .thenReturn(true);

    synchronizer.syncTransactions(reportingDate, Optional.empty(), Optional.empty(), false);

    verify(repository, times(1))
        .save(
            argThat(
                entity ->
                    entity.getSecurityFrom().equals(NEW_SECURITY_FROM)
                        && entity.getSecurityTo().equals(NEW_SECURITY_TO)
                        && entity.getCode().equals(NEW_CODE)
                        && entity.getDateCreated().equals(expectedCreationTime)));

    verify(repository, never())
        .save(argThat(entity -> entity.getSecurityFrom().equals(DUPLICATE_SECURITY_FROM)));
  }

  @Test
  void syncTransactions_whenEpisServiceThrowsException_shouldLogErrorAndNotSave() {
    LocalDate reportingDate = LocalDate.of(2025, 1, 1);
    when(episService.getExchangeTransactions(
            eq(reportingDate), any(Optional.class), any(Optional.class), eq(false)))
        .thenThrow(new RuntimeException("Simulated EpisService error"));

    synchronizer.syncTransactions(reportingDate, Optional.empty(), Optional.empty(), false);

    verify(repository, never()).save(any());
  }
}
