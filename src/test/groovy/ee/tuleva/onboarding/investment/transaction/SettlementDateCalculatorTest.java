package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static ee.tuleva.onboarding.investment.portfolio.Provider.AMUNDI;
import static ee.tuleva.onboarding.investment.portfolio.Provider.CCF;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.instrument.SettlementTerms;
import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementDateCalculatorTest {

  private static final String ETF_ISIN = "IE00BJZ2DC62";
  private static final String IRISH_FUND_ISIN = "IE00BFG1TM61";
  private static final String LUXEMBOURG_FUND_ISIN = "LU1437018838";
  private static final String UNKNOWN_ISIN = "XX0000000000";
  private static final String CCF_ISIN = "IE0009FT4LX4";
  // iShares Euro Aggregate Bond Index Fund — run by Blackrock Luxembourg SA, held by TUK00. Its
  // allocation rows carry provider ISHARES, whose enum domicile is IRELAND.
  private static final String BLACKROCK_LUXEMBOURG_FUND_ISIN = "LU0826455353";
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
  void ccf_submittedBeforeCutoff_acceptedSameDay_settlesThreeIrishBusinessDaysLater() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant mondayBeforeCutoff = tallinnInstant(LocalDate.of(2026, 1, 12), LocalTime.of(9, 15));

    assertThat(calculator().calculateSettlementDate(mondayBeforeCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 1, 15));
  }

  @Test
  void ccf_submittedAfterCutoff_acceptedNextBusinessDay_settlesOneBusinessDayLater() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant mondayAfterCutoff = tallinnInstant(LocalDate.of(2026, 1, 12), LocalTime.of(9, 35));

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
  void ccf_dayCountSkipsIrishHoliday() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant thursdayBeforeStPatricks =
        tallinnInstant(LocalDate.of(2026, 3, 12), LocalTime.of(9, 15));

    assertThat(calculator().calculateSettlementDate(thursdayBeforeStPatricks, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 18));
  }

  @Test
  void ccf_submittedOnIrishHolidayBeforeCutoff_acceptedNextBusinessDay() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant stPatricksBeforeCutoff = tallinnInstant(LocalDate.of(2026, 3, 17), LocalTime.of(9, 0));

    assertThat(calculator().calculateSettlementDate(stPatricksBeforeCutoff, FUND, CCF_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 23));
  }

  @Test
  void ccf_submittedOnSaturdayBeforeCutoff_acceptedOnMonday() {
    given(instrumentReferenceService.settlementTerms(CCF_ISIN)).willReturn(Optional.of(CCF_TERMS));
    givenProvider(CCF_ISIN, CCF);
    Instant saturdayBeforeCutoff = tallinnInstant(LocalDate.of(2026, 1, 17), LocalTime.of(9, 0));

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
    given(
            allocationRepository
                .findFirstByIsinAndProviderIsNotNullAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                    IRISH_FUND_ISIN, tradeDate))
        .willReturn(Optional.empty());

    assertThat(calculator().calculateSettlementDate(tradeDate, FUND, IRISH_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 19));
  }

  @Test
  void fund_domicileComesFromTheInstrumentsCountryNotItsManagers() {
    givenCountry(BLACKROCK_LUXEMBOURG_FUND_ISIN, "LU");
    LocalDate beforeStPatricksDay = LocalDate.of(2026, 3, 12);

    // 17 March is an Irish holiday but not a Luxembourg one. Grouping this fund under its manager
    // (BlackRock → ISHARES → IRELAND) would skip it and settle a day late, on 20 March.
    assertThat(
            calculator()
                .calculateSettlementDate(beforeStPatricksDay, FUND, BLACKROCK_LUXEMBOURG_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 19));
    verifyNoInteractions(allocationRepository);
  }

  @Test
  void fund_withoutACountryFallsBackToItsProvidersDomicile() {
    givenCountry(IRISH_FUND_ISIN, null);
    givenProvider(IRISH_FUND_ISIN, ISHARES);
    LocalDate beforeStPatricksDay = LocalDate.of(2026, 3, 12);

    assertThat(calculator().calculateSettlementDate(beforeStPatricksDay, FUND, IRISH_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 20));
  }

  @Test
  void fund_withAnUnsupportedCountryFallsBackToItsProvidersDomicile() {
    givenCountry(IRISH_FUND_ISIN, "GB");
    givenProvider(IRISH_FUND_ISIN, ISHARES);
    LocalDate beforeStPatricksDay = LocalDate.of(2026, 3, 12);

    assertThat(calculator().calculateSettlementDate(beforeStPatricksDay, FUND, IRISH_FUND_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 20));
  }

  private void givenCountry(String isin, @Nullable String country) {
    InstrumentReference reference = instrument(isin).country(country).build();
    given(instrumentReferenceService.findByIsin(isin)).willReturn(Optional.of(reference));
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
