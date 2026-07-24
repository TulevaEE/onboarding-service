package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.portfolio.Provider.AMUNDI;
import static ee.tuleva.onboarding.investment.portfolio.Provider.CCF;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.instrument.SettlementTerms;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementDateCalculatorTest {

  private static final String ETF_ISIN = "IE00BJZ2DC62";
  private static final String FRENCH_FUND_ISIN = "FR0010688192";
  private static final String IRISH_FUND_ISIN = "IE00BFG1TM61";
  private static final String LUXEMBOURG_FUND_ISIN = "LU1437018838";
  private static final String UNKNOWN_ISIN = "XX0000000000";
  private static final String CCF_ISIN = "IE0009FT4LX4";
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final SettlementTerms CCF_TERMS =
      new SettlementTerms(LocalTime.of(9, 30), TALLINN, 3);

  @Mock private ModelPortfolioAllocationRepository allocationRepository;
  @Mock private InstrumentReferenceService instrumentReferenceService;

  private SettlementDateCalculator calculator() {
    Target2Calendar target2Calendar = new Target2Calendar();
    return new SettlementDateCalculator(
        target2Calendar,
        new DomicileCalendar(target2Calendar),
        allocationRepository,
        instrumentReferenceService);
  }

  private static Instant tallinnInstant(LocalDate date, LocalTime time) {
    return ZonedDateTime.of(date, time, TALLINN).toInstant();
  }

  @Test
  void ccf_submittedBeforeCutoff_acceptedSameDay_settlesThreeFrenchBusinessDaysLater() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant mondayBeforeCutoff = tallinnInstant(LocalDate.of(2026, 1, 12), LocalTime.of(9, 15));

    // T+3 lands on 2026-01-15; the old flat FUND path (+5) would land on 2026-01-19.
    assertThat(calculator().calculateSettlementDate(mondayBeforeCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 15));
  }

  @Test
  void ccf_submittedAfterCutoff_acceptedNextBusinessDay_settlesOneBusinessDayLater() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant mondayAfterCutoff = tallinnInstant(LocalDate.of(2026, 1, 12), LocalTime.of(9, 35));

    // Accepted Tuesday, so T+3 lands on 2026-01-16 -- one business day after the before-cutoff
    // case.
    assertThat(calculator().calculateSettlementDate(mondayAfterCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 16));
  }

  @Test
  void ccf_submittedExactlyAtCutoff_treatedAsBeforeCutoff() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant mondayAtCutoff = tallinnInstant(LocalDate.of(2026, 1, 12), LocalTime.of(9, 30));

    assertThat(calculator().calculateSettlementDate(mondayAtCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 15));
  }

  @Test
  void ccf_dayCountSkipsFrenchHoliday() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    // Bastille Day (French holiday) falls on Tuesday 2026-07-14, inside the T+3 count from Monday.
    Instant mondayBeforeBastille = tallinnInstant(LocalDate.of(2026, 7, 13), LocalTime.of(9, 15));

    // Without the holiday the count would end on 2026-07-16; skipping it pushes to 2026-07-17.
    assertThat(calculator().calculateSettlementDate(mondayBeforeBastille, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 7, 17));
  }

  @Test
  void ccf_submittedOnFrenchHolidayBeforeCutoff_acceptedNextBusinessDay() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant bastilleDayBeforeCutoff = tallinnInstant(LocalDate.of(2026, 7, 14), LocalTime.of(9, 0));

    // Accepted Wednesday 2026-07-15; T+3 French business days lands on Monday 2026-07-20.
    assertThat(calculator().calculateSettlementDate(bastilleDayBeforeCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 7, 20));
  }

  @Test
  void ccf_submittedOnSaturdayBeforeCutoff_acceptedOnMonday() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant saturdayBeforeCutoff = tallinnInstant(LocalDate.of(2026, 1, 17), LocalTime.of(9, 0));

    // Accepted Monday 2026-01-19; T+3 lands on Thursday 2026-01-22.
    assertThat(calculator().calculateSettlementDate(saturdayBeforeCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 22));
  }

  @Test
  void ccf_withNullTerms_fallsBackToFlatFundPath() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.empty());
    givenProvider(CCF_ISIN, CCF);
    Instant monday = tallinnInstant(LocalDate.of(2026, 1, 12), LocalTime.of(9, 15));

    assertThat(calculator().calculateSettlementDate(monday, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 19));
  }

  @Test
  void etf_settlesInTwoTarget2BusinessDays() {
    LocalDate tradeDate = LocalDate.of(2026, 1, 12);

    assertThat(calculator().calculateSettlementDate(tradeDate, ETF, ETF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 14));
  }

  @Test
  void etf_skipsWeekends() {
    LocalDate friday = LocalDate.of(2026, 1, 9);

    assertThat(calculator().calculateSettlementDate(friday, ETF, ETF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 13));
  }

  @Test
  void etf_skipsGoodFridayAndEasterMonday() {
    LocalDate beforeEaster2026 = LocalDate.of(2026, 4, 1);

    assertThat(calculator().calculateSettlementDate(beforeEaster2026, ETF, ETF_ISIN))
        .isEqualTo(LocalDate.of(2026, 4, 7));
  }

  @Test
  void etf_skipsEasterBreak2025() {
    LocalDate maundyThursday2025 = LocalDate.of(2025, 4, 17);

    assertThat(calculator().calculateSettlementDate(maundyThursday2025, ETF, ETF_ISIN))
        .isEqualTo(LocalDate.of(2025, 4, 23));
  }

  @Test
  void fund_settlesInFiveBusinessDaysOnProviderDomicileCalendar() {
    givenProvider(FRENCH_FUND_ISIN, CCF);
    LocalDate beforeBastilleDay = LocalDate.of(2026, 7, 8);

    assertThat(calculator().calculateSettlementDate(beforeBastilleDay, FUND, FRENCH_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 7, 16));
  }

  @Test
  void fund_irishProviderSkipsStPatricksDay() {
    givenProvider(IRISH_FUND_ISIN, ISHARES);
    LocalDate beforeStPatricksDay = LocalDate.of(2026, 3, 12);

    assertThat(calculator().calculateSettlementDate(beforeStPatricksDay, FUND, IRISH_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 20));
  }

  @Test
  void fund_luxembourgProviderSkipsAscensionDay() {
    givenProvider(LUXEMBOURG_FUND_ISIN, AMUNDI);
    LocalDate beforeAscension2026 = LocalDate.of(2026, 5, 7);

    assertThat(
            calculator().calculateSettlementDate(beforeAscension2026, FUND, LUXEMBOURG_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 5, 15));
  }

  @Test
  void fund_unresolvableIsinFallsBackToTarget2() {
    LocalDate beforeStPatricksDay = LocalDate.of(2026, 3, 12);
    given(
            allocationRepository
                .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                    UNKNOWN_ISIN, beforeStPatricksDay))
        .willReturn(Optional.empty());

    assertThat(calculator().calculateSettlementDate(beforeStPatricksDay, FUND, UNKNOWN_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 19));
  }

  @Test
  void fund_futureDatedAllocationDoesNotAffectEarlierTradeDate() {
    LocalDate tradeDate = LocalDate.of(2026, 3, 12);
    // A future-dated allocation (effectiveDate after the trade date) must not be resolved as the
    // provider for this trade. The as-of-bounded finder returns empty, so we fall back to TARGET2.
    given(
            allocationRepository
                .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                    IRISH_FUND_ISIN, tradeDate))
        .willReturn(Optional.empty());

    // If the future allocation leaked in, the Irish calendar would skip St Patrick's Day and push
    // settlement to 2026-03-20; with TARGET2 fallback it settles 2026-03-19.
    assertThat(calculator().calculateSettlementDate(tradeDate, FUND, IRISH_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 19));
  }

  private void givenProvider(String isin, Provider provider) {
    given(
            allocationRepository
                .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                    eq(isin), any()))
        .willReturn(
            Optional.of(ModelPortfolioAllocation.builder().isin(isin).provider(provider).build()));
  }
}
