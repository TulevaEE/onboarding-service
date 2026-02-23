package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.getPillar2Funds;
import static ee.tuleva.onboarding.fund.TulevaFund.getPillar3Funds;
import static ee.tuleva.onboarding.fund.TulevaFund.getSavingsFunds;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionCalculationJobTest {

  private static final TulevaFund FUND = TUK75;
  private static final String ISIN = "IE00BFNM3G45";
  private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
  private static final LocalTime MORNING_CUTOFF = LocalTime.of(11, 30);
  private static final LocalTime AFTERNOON_CUTOFF = LocalTime.of(15, 30);

  @Mock private PositionCalculationService calculationService;
  @Mock private PositionCalculationPersistenceService persistenceService;
  @Mock private PositionCalculationNotifier notifier;

  @InjectMocks private PositionCalculationJob job;

  @Test
  void calculateForFunds_withOkStatus_savesWithoutNotification() {
    List<PositionCalculation> calculations = List.of(createCalculation(OK));
    when(calculationService.calculateForLatestDate(any(List.class), isNull()))
        .thenReturn(calculations);

    job.calculateForFunds(List.of(FUND));

    verify(persistenceService).saveAll(calculations);
    verify(notifier, never()).notifyNoPriceData(any(), any(), any());
  }

  @Test
  void calculateForFunds_withNoPriceData_notifiesBlocked() {
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("1000"))
            .validationStatus(NO_PRICE_DATA)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculateForLatestDate(any(List.class), isNull()))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier).notifyNoPriceData(FUND, ISIN, DATE);
  }

  @Test
  void calculateForFunds_withException_doesNotThrow() {
    when(calculationService.calculateForLatestDate(any(List.class), isNull()))
        .thenThrow(new RuntimeException("Test exception"));

    job.calculateForFunds(List.of(FUND));

    verify(persistenceService, never()).saveAll(any());
  }

  @Test
  void calculateForFunds_withMultipleFunds_processesAll() {
    List<TulevaFund> funds = List.of(TUK75, TUK00);
    List<PositionCalculation> calculations = List.of(createCalculation(OK));
    when(calculationService.calculateForLatestDate(eq(funds), isNull())).thenReturn(calculations);

    job.calculateForFunds(funds);

    verify(calculationService).calculateForLatestDate(funds, null);
    verify(persistenceService).saveAll(calculations);
  }

  @Test
  void calculatePositionsMorning_processesPillarIIFunds() {
    List<TulevaFund> expectedFunds = getPillar2Funds();
    when(calculationService.calculateForLatestDate(eq(expectedFunds), eq(MORNING_CUTOFF)))
        .thenReturn(List.of());

    job.calculatePositionsMorning();

    verify(calculationService).calculateForLatestDate(expectedFunds, MORNING_CUTOFF);
  }

  @Test
  void calculatePositionsAfternoon_processesPillarIIIAndSavingsFunds() {
    var expectedFunds =
        Stream.concat(getPillar3Funds().stream(), getSavingsFunds().stream()).toList();
    when(calculationService.calculateForLatestDate(eq(expectedFunds), eq(AFTERNOON_CUTOFF)))
        .thenReturn(List.of());

    job.calculatePositionsAfternoon();

    verify(calculationService).calculateForLatestDate(expectedFunds, AFTERNOON_CUTOFF);
  }

  @Test
  void calculateForFunds_withStalePrice_notifiesStalePrice() {
    LocalDate staleDate = DATE.minusDays(3);
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("1000"))
            .usedPrice(new BigDecimal("100.00"))
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(OK)
            .priceDate(staleDate)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculateForLatestDate(any(List.class), isNull()))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier).notifyStalePrice(FUND, ISIN, DATE, staleDate);
  }

  @Test
  void calculateForFunds_withCurrentPrice_doesNotNotifyStalePrice() {
    PositionCalculation calculation = createCalculation(OK);

    when(calculationService.calculateForLatestDate(any(List.class), isNull()))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier, never()).notifyStalePrice(any(), any(), any(), any());
  }

  private PositionCalculation createCalculation(ValidationStatus status) {
    return PositionCalculation.builder()
        .isin(ISIN)
        .fund(FUND)
        .date(DATE)
        .quantity(new BigDecimal("1000"))
        .usedPrice(new BigDecimal("100.00"))
        .priceSource(EODHD)
        .calculatedMarketValue(new BigDecimal("100000.00"))
        .validationStatus(status)
        .priceDate(DATE)
        .createdAt(Instant.now())
        .build();
  }
}
