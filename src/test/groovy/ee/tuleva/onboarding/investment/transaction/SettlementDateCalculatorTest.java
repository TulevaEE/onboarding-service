package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.portfolio.Provider.AMUNDI;
import static ee.tuleva.onboarding.investment.portfolio.Provider.CCF;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.calendar.DomicileCalendar;
import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.portfolio.Provider;
import java.time.LocalDate;
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

  @Mock private ModelPortfolioAllocationRepository allocationRepository;

  private SettlementDateCalculator calculator() {
    Target2Calendar target2Calendar = new Target2Calendar();
    return new SettlementDateCalculator(
        target2Calendar, new DomicileCalendar(target2Calendar), allocationRepository);
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
    given(
            allocationRepository.findFirstByIsinAndProviderIsNotNullOrderByEffectiveDateDesc(
                UNKNOWN_ISIN))
        .willReturn(Optional.empty());
    LocalDate beforeStPatricksDay = LocalDate.of(2026, 3, 12);

    assertThat(calculator().calculateSettlementDate(beforeStPatricksDay, FUND, UNKNOWN_ISIN))
        .isEqualTo(LocalDate.of(2026, 3, 19));
  }

  private void givenProvider(String isin, Provider provider) {
    given(allocationRepository.findFirstByIsinAndProviderIsNotNullOrderByEffectiveDateDesc(isin))
        .willReturn(
            Optional.of(ModelPortfolioAllocation.builder().isin(isin).provider(provider).build()));
  }
}
