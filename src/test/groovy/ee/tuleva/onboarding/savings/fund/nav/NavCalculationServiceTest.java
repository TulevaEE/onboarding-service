package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.fundUnitsOutstandingAccount;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.investment.fees.FeeCalculationService;
import ee.tuleva.onboarding.investment.fees.FeeResult;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.LedgerAccountFixture.EntryFixture;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.components.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavCalculationServiceTest {

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private PublicHolidays publicHolidays;
  @Mock private LedgerService ledgerService;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private SecuritiesValueComponent securitiesValueComponent;
  @Mock private CashPositionComponent cashPositionComponent;
  @Mock private ReceivablesComponent receivablesComponent;
  @Mock private PayablesComponent payablesComponent;
  @Mock private SubscriptionsComponent subscriptionsComponent;
  @Mock private RedemptionsComponent redemptionsComponent;
  @Mock private BlackrockAdjustmentComponent blackrockAdjustmentComponent;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private FeeCalculationService feeCalculationService;

  private NavCalculationService service;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2025-01-15T14:00:00Z"), ZoneOffset.UTC);
    service =
        new NavCalculationService(
            fundPositionRepository,
            publicHolidays,
            ledgerService,
            navLedgerRepository,
            securitiesValueComponent,
            cashPositionComponent,
            receivablesComponent,
            payablesComponent,
            subscriptionsComponent,
            redemptionsComponent,
            blackrockAdjustmentComponent,
            positionPriceResolver,
            feeCalculationService,
            fixedClock);
  }

  @Test
  void calculate_performsTwoPassCalculationWithRedemptions() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(previousWorkingDay));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100))
        .thenReturn(fundUnitsOutstandingAccount(new BigDecimal("100000.00000")));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("900000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(new BigDecimal("50000.00"));
    when(receivablesComponent.calculate(any())).thenReturn(new BigDecimal("10000.00"));
    when(payablesComponent.calculate(any())).thenReturn(new BigDecimal("5000.00"));
    when(subscriptionsComponent.calculate(any())).thenReturn(new BigDecimal("25000.00"));
    BigDecimal expectedBaseValue = new BigDecimal("955000.00");
    when(feeCalculationService.calculateFeesForNav(
            eq(TKF100), eq(previousWorkingDay), eq(expectedBaseValue), any(), any()))
        .thenReturn(new FeeResult(new BigDecimal("52.08"), new BigDecimal("6.85")));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(new BigDecimal("500.00"));
    when(redemptionsComponent.calculate(any())).thenReturn(new BigDecimal("10500.00"));

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.fund()).isEqualTo(TKF100);
    assertThat(result.calculationDate()).isEqualTo(calcDate);
    assertThat(result.securitiesValue()).isEqualByComparingTo("900000.00");
    assertThat(result.cashPosition()).isEqualByComparingTo("50000.00");
    assertThat(result.receivables()).isEqualByComparingTo("10000.00");
    assertThat(result.pendingSubscriptions()).isEqualByComparingTo("25000.00");
    assertThat(result.payables()).isEqualByComparingTo("5000.00");
    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("52.08");
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo("6.85");
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo("500.00");
    assertThat(result.pendingRedemptions()).isEqualByComparingTo("10500.00");
    assertThat(result.unitsOutstanding()).isEqualByComparingTo("100000.00000");

    BigDecimal expectedPreliminaryNav =
        new BigDecimal("900000.00")
            .add(new BigDecimal("50000.00"))
            .add(new BigDecimal("10000.00"))
            .add(new BigDecimal("25000.00"))
            .add(new BigDecimal("500.00"))
            .subtract(new BigDecimal("5000.00"))
            .subtract(new BigDecimal("52.08"))
            .subtract(new BigDecimal("6.85"));

    BigDecimal expectedTotalNav = expectedPreliminaryNav.subtract(new BigDecimal("10500.00"));

    assertThat(result.aum()).isEqualByComparingTo(expectedTotalNav);
    assertThat(result.navPerUnit().compareTo(ZERO)).isGreaterThan(0);
  }

  @Test
  void calculate_returnsNavOfOneWhenNoUnitsOutstanding() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(previousWorkingDay));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100))
        .thenReturn(fundUnitsOutstandingAccount(ZERO));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.navPerUnit()).isEqualByComparingTo("1");
    assertThat(result.unitsOutstanding()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_throwsWhenNoPositionReport() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.calculate(TKF100, calcDate))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No position report found");
  }

  @Test
  void calculate_throwsWhenPositionDataIsStale() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    LocalDate staleDate = LocalDate.of(2025, 1, 13);

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(staleDate));

    assertThatThrownBy(() -> service.calculate(TKF100, calcDate))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void calculate_handlesNegativeBlackrockAdjustmentAsLiability() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(previousWorkingDay));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100))
        .thenReturn(fundUnitsOutstandingAccount(new BigDecimal("100000.00000")));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(new BigDecimal("-300.00"));
    when(redemptionsComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.aum()).isEqualByComparingTo("999700.00");
  }

  @Test
  void calculate_looksUpPositionReportBeforeCalculationDate() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    LocalDate positionDate = LocalDate.of(2025, 1, 14);

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(positionDate));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100))
        .thenReturn(fundUnitsOutstandingAccount(new BigDecimal("100000.00000")));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(ZERO);
    when(redemptionsComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.positionReportDate()).isEqualTo(positionDate);
  }

  @Test
  void calculate_usesSummerTimeCutoffForUnitsOutstanding() {
    LocalDate summerDate = LocalDate.of(2025, 7, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 7, 14);

    var account =
        fundUnitsOutstandingAccount(
            List.of(
                new EntryFixture(
                    new BigDecimal("80000.00000"), Instant.parse("2025-07-15T12:00:00Z")),
                new EntryFixture(
                    new BigDecimal("20000.00000"), Instant.parse("2025-07-15T13:30:00Z"))));

    when(publicHolidays.previousWorkingDay(summerDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(previousWorkingDay));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100)).thenReturn(account);

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(ZERO);
    when(redemptionsComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TKF100, summerDate);

    assertThat(result.unitsOutstanding()).isEqualByComparingTo("80000.00000");
  }

  @Test
  void calculate_usesFundSpecificCutoffForUnitsOutstanding() {
    // TUK75 cutoff is 11:00 EET = 09:00 UTC (winter)
    LocalDate calcDate = LocalDate.of(2026, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2026, 1, 14);

    var account =
        fundUnitsOutstandingAccount(
            TUK75,
            List.of(
                new EntryFixture(
                    new BigDecimal("80000.00000"), Instant.parse("2026-01-15T08:00:00Z")),
                new EntryFixture(
                    new BigDecimal("20000.00000"), Instant.parse("2026-01-15T09:30:00Z"))));

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, previousWorkingDay))
        .thenReturn(Optional.of(previousWorkingDay));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TUK75)).thenReturn(account);

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(ZERO);
    when(redemptionsComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TUK75, calcDate);

    // Only the 80000 entry (08:00 UTC) should be included; 20000 entry (09:30 UTC) is after cutoff
    assertThat(result.unitsOutstanding()).isEqualByComparingTo("80000.00000");
  }

  @Test
  void calculate_securitiesDetailUsesCutoffForPriceResolution() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    // TKF100 cutoff 15:30 EET = 13:30 UTC (winter)
    Instant expectedCutoff = Instant.parse("2025-01-15T13:30:00Z");
    Instant expectedPriceCutoff = Instant.parse("2025-01-15T13:35:00Z");

    when(publicHolidays.previousWorkingDay(calcDate)).thenReturn(previousWorkingDay);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, previousWorkingDay))
        .thenReturn(Optional.of(previousWorkingDay));
    when(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100))
        .thenReturn(fundUnitsOutstandingAccount(new BigDecimal("100000.00000")));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(ZERO);
    when(redemptionsComponent.calculate(any())).thenReturn(ZERO);

    when(navLedgerRepository.getSecuritiesUnitBalancesAt(expectedCutoff, TKF100))
        .thenReturn(Map.of("IE00BFG1TM61", new BigDecimal("1000.00000")));
    when(positionPriceResolver.resolve("IE00BFG1TM61", previousWorkingDay, expectedPriceCutoff))
        .thenReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal("34.00"))
                    .validationStatus(OK)
                    .priceDate(previousWorkingDay)
                    .storageKey("IE00BFG1TM61.EUFUND")
                    .build()));

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.securitiesDetail()).hasSize(1);
    assertThat(result.securitiesDetail().getFirst().price()).isEqualByComparingTo("34.00");
  }

  @Test
  void backfillFees_calculatesFeesForAllDaysIncludingWeekends() {
    LocalDate friday = LocalDate.of(2026, 3, 6);
    LocalDate sunday = LocalDate.of(2026, 3, 8);

    // backfill for fri/sat/sun calls computeFeeBaseValue with +1 day:
    // fri+1=sat, sat+1=sun, sun+1=mon — all resolve via previousWorkingDay to friday
    when(publicHolidays.previousWorkingDay(LocalDate.of(2026, 3, 7))).thenReturn(friday);
    when(publicHolidays.previousWorkingDay(LocalDate.of(2026, 3, 8))).thenReturn(friday);
    when(publicHolidays.previousWorkingDay(LocalDate.of(2026, 3, 9))).thenReturn(friday);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, friday))
        .thenReturn(Optional.of(friday));
    when(securitiesValueComponent.calculate(any())).thenReturn(ZERO);
    when(cashPositionComponent.calculate(any())).thenReturn(new BigDecimal("1000000"));
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));

    service.backfillFees(TKF100, friday, sunday);

    verify(feeCalculationService, times(3))
        .calculateFeesForNav(eq(TKF100), any(), eq(new BigDecimal("1000000")), any(), any());
    verify(feeCalculationService).calculateFeesForNav(eq(TKF100), eq(friday), any(), any(), any());
    verify(feeCalculationService)
        .calculateFeesForNav(eq(TKF100), eq(LocalDate.of(2026, 3, 7)), any(), any(), any());
    verify(feeCalculationService).calculateFeesForNav(eq(TKF100), eq(sunday), any(), any(), any());
  }

  @Test
  void backfillFees_usesCalendarDatePositionNotPreviousWorkingDay() {
    // Fee for Monday Mar 2 should use Monday's position, not Friday's
    LocalDate monday = LocalDate.of(2026, 3, 2);
    LocalDate tuesday = LocalDate.of(2026, 3, 3);

    // backfill calls computeFeeBaseValue(fund, tuesday) → previousWorkingDay(tuesday) = monday
    when(publicHolidays.previousWorkingDay(tuesday)).thenReturn(monday);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, monday))
        .thenReturn(Optional.of(monday));
    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("900000000"));
    when(cashPositionComponent.calculate(any())).thenReturn(new BigDecimal("50000000"));
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(feeCalculationService.calculateFeesForNav(any(), any(), any(), any(), any()))
        .thenReturn(new FeeResult(ZERO, ZERO));

    service.backfillFees(TUK75, monday, monday);

    // Fee date is Monday, base value uses Monday's position (950M)
    verify(feeCalculationService)
        .calculateFeesForNav(eq(TUK75), eq(monday), eq(new BigDecimal("950000000")), any(), any());
  }

  @Test
  void backfillFees_skipsDateWithNoPositionReport() {
    LocalDate date = LocalDate.of(2026, 3, 6);
    LocalDate nextDay = LocalDate.of(2026, 3, 7);

    // backfill calls computeFeeBaseValue(fund, date+1) → previousWorkingDay(nextDay) = date
    when(publicHolidays.previousWorkingDay(nextDay)).thenReturn(date);
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, date))
        .thenReturn(Optional.empty());

    service.backfillFees(TKF100, date, date);

    verify(feeCalculationService, never()).calculateFeesForNav(any(), any(), any(), any(), any());
  }

  @Test
  void computeFeeBaseValue_usesSameDayPositionOnInceptionDate() {
    LocalDate inceptionDate = TKF100.getInceptionDate();

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, inceptionDate))
        .thenReturn(Optional.of(inceptionDate));
    when(securitiesValueComponent.calculate(any())).thenReturn(ZERO);
    when(cashPositionComponent.calculate(any())).thenReturn(new BigDecimal("5500000.00"));
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);

    var result = service.computeFeeBaseValue(TKF100, inceptionDate);

    assertThat(result).isPresent();
    assertThat(result.get().positionReportDate()).isEqualTo(inceptionDate);
    assertThat(result.get().baseValue()).isEqualByComparingTo("5500000.00");
    verify(publicHolidays, never()).previousWorkingDay(any());
  }

  @Test
  void computeFeeBaseValue_returnsEmptyWhenNoPositionReport() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(publicHolidays.previousWorkingDay(date)).thenReturn(LocalDate.of(2026, 1, 14));
    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(
            TKF100, LocalDate.of(2026, 1, 14)))
        .thenReturn(Optional.empty());

    var result = service.computeFeeBaseValue(TKF100, date);

    assertThat(result).isEmpty();
  }
}
