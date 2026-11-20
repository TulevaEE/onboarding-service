package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_WARNING_CALENDAR_DAYS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RebalanceWindowsTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 12, 2);

  private final ModelPortfolioAllocationRepository allocationRepository =
      mock(ModelPortfolioAllocationRepository.class);
  private final NavWeightsReader navWeightsReader = mock(NavWeightsReader.class);
  private final InvestmentParameterRepository parameterRepository =
      mock(InvestmentParameterRepository.class);
  private final RebalanceWindows rebalanceWindows =
      new RebalanceWindows(
          allocationRepository,
          navWeightsReader,
          parameterRepository,
          new EstonianCalendar(new PublicHolidays()));

  @Test
  void aFundsModelHistoryAndTheParametersAreLoadedOnceHoweverManyWindowsAreMeasured() {
    given(allocationRepository.findByFundAndEffectiveDateLessThanEqual(TUV100, CHECK_DATE))
        .willReturn(
            List.of(
                allocation(TUV100, LocalDate.of(2026, 6, 1), "WORLD"),
                allocation(TUV100, LocalDate.of(2026, 8, 1), "EUROPE"),
                allocation(TUV100, LocalDate.of(2026, 9, 1), "NEW_WORLD"),
                allocation(TUV100, LocalDate.of(2026, 10, 1), "USA")));
    given(parameterRepository.findLatestValue(MODEL_REBALANCE_TOLERANCE, CHECK_DATE))
        .willReturn(new BigDecimal("0.05"));
    given(parameterRepository.findLatestValue(MODEL_REBALANCE_WARNING_CALENDAR_DAYS, CHECK_DATE))
        .willReturn(new BigDecimal("15"));
    given(navWeightsReader.between(any(), any(), any())).willReturn(List.of());

    var windows = rebalanceWindows.of(TUV100, CHECK_DATE, modelEffectiveDate -> true);

    assertThat(windows.current())
        .map(RebalanceWindow::modelEffectiveDate)
        .contains(LocalDate.of(2026, 10, 1));
    assertThat(windows.superseded())
        .extracting(superseded -> superseded.window().modelEffectiveDate())
        .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1));
    then(allocationRepository)
        .should(times(1))
        .findByFundAndEffectiveDateLessThanEqual(TUV100, CHECK_DATE);
    then(parameterRepository)
        .should(times(1))
        .findLatestValue(MODEL_REBALANCE_TOLERANCE, CHECK_DATE);
    then(parameterRepository)
        .should(times(1))
        .findLatestValue(MODEL_REBALANCE_WARNING_CALENDAR_DAYS, CHECK_DATE);
  }

  @Test
  void aFundWithoutAModelChange_hasNoWindowsAndLoadsNoParameters() {
    given(allocationRepository.findByFundAndEffectiveDateLessThanEqual(TUK75, CHECK_DATE))
        .willReturn(List.of(allocation(TUK75, LocalDate.of(2026, 6, 1), "WORLD")));

    var windows = rebalanceWindows.of(TUK75, CHECK_DATE, modelEffectiveDate -> true);

    assertThat(windows.current()).isEmpty();
    assertThat(windows.superseded()).isEmpty();
    then(parameterRepository).shouldHaveNoInteractions();
  }

  private static ModelPortfolioAllocation allocation(
      TulevaFund fund, LocalDate effectiveDate, String isin) {
    return ModelPortfolioAllocation.builder()
        .fund(fund)
        .effectiveDate(effectiveDate)
        .isin(isin)
        .weight(BigDecimal.ONE)
        .build();
  }
}
