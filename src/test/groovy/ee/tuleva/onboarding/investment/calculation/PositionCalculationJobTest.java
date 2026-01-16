package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.PriceSource.EODHD;
import static ee.tuleva.onboarding.investment.calculation.PriceSource.YAHOO;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.getPillar2Funds;
import static ee.tuleva.onboarding.investment.calculation.TulevaFund.getPillar3Funds;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.EODHD_MISSING;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.PRICE_DISCREPANCY;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.YAHOO_MISSING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);
  private static final LocalDate TWO_DAYS_AGO = LocalDate.now().minusDays(2);

  @Mock private PositionCalculationService calculationService;
  @Mock private PositionCalculationPersistenceService persistenceService;
  @Mock private PositionCalculationNotifier notifier;

  @InjectMocks private PositionCalculationJob job;

  @Test
  void calculateForFunds_withOkStatus_savesWithoutNotification() {
    List<PositionCalculation> calculations = List.of(createCalculation(OK));
    when(calculationService.calculate(any(List.class), eq(YESTERDAY))).thenReturn(calculations);

    job.calculateForFunds(List.of(FUND), 1);

    verify(persistenceService).saveAll(calculations);
    verify(notifier, never()).notifyPriceDiscrepancy(any(), any(), any(), any(), any(), any());
    verify(notifier, never()).notifyYahooMissing(any(), any(), any(), any());
    verify(notifier, never()).notifyEodhdMissing(any(), any(), any(), any());
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
            .date(YESTERDAY)
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

    when(calculationService.calculate(any(List.class), eq(YESTERDAY)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND), 1);

    verify(notifier)
        .notifyPriceDiscrepancy(FUND, ISIN, YESTERDAY, eodhdPrice, yahooPrice, discrepancy);
  }

  @Test
  void calculateForFunds_withYahooMissing_notifiesMissingYahoo() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(YESTERDAY)
            .quantity(new BigDecimal("1000"))
            .eodhdPrice(eodhdPrice)
            .usedPrice(eodhdPrice)
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(YAHOO_MISSING)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculate(any(List.class), eq(YESTERDAY)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND), 1);

    verify(notifier).notifyYahooMissing(FUND, ISIN, YESTERDAY, eodhdPrice);
  }

  @Test
  void calculateForFunds_withEodhdMissing_notifiesMissingEodhd() {
    BigDecimal yahooPrice = new BigDecimal("100.00");
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(YESTERDAY)
            .quantity(new BigDecimal("1000"))
            .yahooPrice(yahooPrice)
            .usedPrice(yahooPrice)
            .priceSource(YAHOO)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(EODHD_MISSING)
            .priceDate(YESTERDAY)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculate(any(List.class), eq(YESTERDAY)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND), 1);

    verify(notifier).notifyEodhdMissing(FUND, ISIN, YESTERDAY, yahooPrice);
  }

  @Test
  void calculateForFunds_withNoPriceData_notifiesBlocked() {
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(YESTERDAY)
            .quantity(new BigDecimal("1000"))
            .validationStatus(NO_PRICE_DATA)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculate(any(List.class), eq(YESTERDAY)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND), 1);

    verify(notifier).notifyNoPriceData(FUND, ISIN, YESTERDAY);
  }

  @Test
  void calculateForFunds_withException_doesNotThrow() {
    when(calculationService.calculate(any(List.class), any()))
        .thenThrow(new RuntimeException("Test exception"));

    job.calculateForFunds(List.of(FUND), 1);

    verify(persistenceService, never()).saveAll(any());
  }

  @Test
  void calculateForFunds_withMultipleFunds_processesAll() {
    List<TulevaFund> funds = List.of(TUK75, TUK00);
    List<PositionCalculation> calculations = List.of(createCalculation(OK));
    when(calculationService.calculate(eq(funds), eq(YESTERDAY))).thenReturn(calculations);

    job.calculateForFunds(funds, 1);

    verify(calculationService).calculate(funds, YESTERDAY);
    verify(persistenceService).saveAll(calculations);
  }

  @Test
  void calculatePositions1130_processesPillarIIFundsWithTwoDaysAgo() {
    List<TulevaFund> expectedFunds = getPillar2Funds();
    when(calculationService.calculate(eq(expectedFunds), eq(TWO_DAYS_AGO))).thenReturn(List.of());

    job.calculatePositions1130();

    verify(calculationService).calculate(expectedFunds, TWO_DAYS_AGO);
  }

  @Test
  void calculatePositions1530_processesPillarIIIFunds() {
    List<TulevaFund> expectedFunds = getPillar3Funds();
    when(calculationService.calculate(eq(expectedFunds), eq(YESTERDAY))).thenReturn(List.of());

    job.calculatePositions1530();

    verify(calculationService).calculate(expectedFunds, YESTERDAY);
  }

  @Test
  void calculateForFunds_withStalePrice_notifiesStalePrice() {
    LocalDate staleDate = YESTERDAY.minusDays(3);
    PositionCalculation calculation =
        PositionCalculation.builder()
            .isin(ISIN)
            .fund(FUND)
            .date(YESTERDAY)
            .quantity(new BigDecimal("1000"))
            .eodhdPrice(new BigDecimal("100.00"))
            .usedPrice(new BigDecimal("100.00"))
            .priceSource(EODHD)
            .calculatedMarketValue(new BigDecimal("100000.00"))
            .validationStatus(OK)
            .priceDate(staleDate)
            .createdAt(Instant.now())
            .build();

    when(calculationService.calculate(any(List.class), eq(YESTERDAY)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND), 1);

    verify(notifier).notifyStalePrice(FUND, ISIN, YESTERDAY, staleDate);
  }

  @Test
  void calculateForFunds_withCurrentPrice_doesNotNotifyStalePrice() {
    PositionCalculation calculation = createCalculation(OK);

    when(calculationService.calculate(any(List.class), eq(YESTERDAY)))
        .thenReturn(List.of(calculation));

    job.calculateForFunds(List.of(FUND), 1);

    verify(notifier, never()).notifyStalePrice(any(), any(), any(), any());
  }

  private PositionCalculation createCalculation(ValidationStatus status) {
    return PositionCalculation.builder()
        .isin(ISIN)
        .fund(FUND)
        .date(YESTERDAY)
        .quantity(new BigDecimal("1000"))
        .eodhdPrice(new BigDecimal("100.00"))
        .usedPrice(new BigDecimal("100.00"))
        .priceSource(EODHD)
        .calculatedMarketValue(new BigDecimal("100000.00"))
        .validationStatus(status)
        .priceDate(YESTERDAY)
        .createdAt(Instant.now())
        .build();
  }
}
