package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.TulevaFund.getPillar2Funds;
import static ee.tuleva.onboarding.investment.TulevaFund.getPillar3Funds;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.PRICE_DISCREPANCY;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.YAHOO_MISSING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

  @Mock private PositionCalculationService calculationService;
  @Mock private PositionCalculationPersistenceService persistenceService;
  @Mock private PositionCalculationNotifier notifier;

  @InjectMocks private PositionCalculationJob job;

  @Test
  void calculateForFunds_withOkStatus_savesWithoutNotification() {
    List<PositionCalculation> calculations = List.of(createCalculation(OK));
    when(calculationService.calculateForLatestDate(any(List.class))).thenReturn(calculations);

    job.calculateForFunds(List.of(FUND));

    verify(persistenceService).saveAll(calculations);
    verify(notifier, never()).notifyPriceDiscrepancy(any(), any(), any(), any(), any(), any());
    verify(notifier, never()).notifyYahooMissing(any(), any(), any(), any());
    verify(notifier, never()).notifyNoPriceData(any(), any(), any());
  }

  @Test
  void calculateForFunds_withPriceDiscrepancy_notifiesDiscrepancy() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    BigDecimal yahooPrice = new BigDecimal("101.00");
    BigDecimal discrepancy = new BigDecimal("1.0");
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("1000"))
            .eodhdPrice(eodhdPrice)
            .yahooPrice(yahooPrice)
            .usedPrice(eodhdPrice)
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(PRICE_DISCREPANCY)
            .priceDiscrepancyPercent(discrepancy)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculateForLatestDate(any(List.class)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier).notifyPriceDiscrepancy(FUND, ISIN, DATE, eodhdPrice, yahooPrice, discrepancy);
  }

  @Test
  void calculateForFunds_withYahooMissing_notifiesMissingYahoo() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(DATE)
            .quantity(new BigDecimal("1000"))
            .eodhdPrice(eodhdPrice)
            .usedPrice(eodhdPrice)
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(YAHOO_MISSING)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculateForLatestDate(any(List.class)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier).notifyYahooMissing(FUND, ISIN, DATE, eodhdPrice);
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

    when(calculationService.calculateForLatestDate(any(List.class)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier).notifyNoPriceData(FUND, ISIN, DATE);
  }

  @Test
  void calculateForFunds_withException_doesNotThrow() {
    when(calculationService.calculateForLatestDate(any(List.class)))
        .thenThrow(new RuntimeException("Test exception"));

    job.calculateForFunds(List.of(FUND));

    verify(persistenceService, never()).saveAll(any());
  }

  @Test
  void calculateForFunds_withMultipleFunds_processesAll() {
    List<TulevaFund> funds = List.of(TUK75, TUK00);
    List<PositionCalculation> calculations = List.of(createCalculation(OK));
    when(calculationService.calculateForLatestDate(funds)).thenReturn(calculations);

    job.calculateForFunds(funds);

    verify(calculationService).calculateForLatestDate(funds);
    verify(persistenceService).saveAll(calculations);
  }

  @Test
  void calculatePositionsMorning_processesPillarIIFunds() {
    List<TulevaFund> expectedFunds = getPillar2Funds();
    when(calculationService.calculateForLatestDate(expectedFunds)).thenReturn(List.of());

    job.calculatePositionsMorning();

    verify(calculationService).calculateForLatestDate(expectedFunds);
  }

  @Test
  void calculatePositionsAfternoon_processesPillarIIIFunds() {
    List<TulevaFund> expectedFunds = getPillar3Funds();
    when(calculationService.calculateForLatestDate(expectedFunds)).thenReturn(List.of());

    job.calculatePositionsAfternoon();

    verify(calculationService).calculateForLatestDate(expectedFunds);
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
            .eodhdPrice(new BigDecimal("100.00"))
            .usedPrice(new BigDecimal("100.00"))
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(OK)
            .priceDate(staleDate)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculateForLatestDate(any(List.class)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND));

    verify(notifier).notifyStalePrice(FUND, ISIN, DATE, staleDate);
  }

  @Test
  void calculateForFunds_withCurrentPrice_doesNotNotifyStalePrice() {
    PositionCalculation calculation = createCalculation(OK);

    when(calculationService.calculateForLatestDate(any(List.class)))
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
        .eodhdPrice(new BigDecimal("100.00"))
        .usedPrice(new BigDecimal("100.00"))
        .priceSource(EODHD)
        .calculatedMarketValue(new BigDecimal("100000.00"))
        .validationStatus(status)
        .priceDate(DATE)
        .createdAt(Instant.now())
        .build();
  }
}
