package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RebalanceWindowsTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 12, 2);

  private final ModelPortfolioAllocationRepository allocationRepository =
      mock(ModelPortfolioAllocationRepository.class);
  private final RebalanceTrades rebalanceTrades = mock(RebalanceTrades.class);
  private final NavWeightsReader navWeightsReader = mock(NavWeightsReader.class);
  private final InvestmentParameterRepository parameterRepository =
      mock(InvestmentParameterRepository.class);
  private final EstonianCalendar estonianCalendar = new EstonianCalendar(new PublicHolidays());
  private final RebalanceWindows rebalanceWindows =
      new RebalanceWindows(
          allocationRepository,
          rebalanceTrades,
          navWeightsReader,
          parameterRepository,
          estonianCalendar);

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
    given(rebalanceTrades.firstRebalancingTradeDate(any(), any(), any()))
        .willReturn(Optional.empty());

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
  void
      aRebalanceOrderTheCustodianExecutesBetweenTheTwoTradeReads_stillClosesTheWindowOnItsTradeDate() {
    var effectiveDate = LocalDate.of(2026, 9, 1);
    var pastTheDeadline = LocalDate.of(2026, 12, 3);
    var tradeDate = LocalDate.of(2026, 12, 1);
    given(allocationRepository.findByFundAndEffectiveDateLessThanEqual(TUV100, pastTheDeadline))
        .willReturn(
            List.of(
                allocation(TUV100, LocalDate.of(2026, 6, 1), "WORLD"),
                allocation(TUV100, effectiveDate, "NEW_WORLD")));
    given(parameterRepository.findLatestValue(MODEL_REBALANCE_TOLERANCE, pastTheDeadline))
        .willReturn(new BigDecimal("0.05"));
    given(
            parameterRepository.findLatestValue(
                MODEL_REBALANCE_WARNING_CALENDAR_DAYS, pastTheDeadline))
        .willReturn(new BigDecimal("15"));
    given(navWeightsReader.between(any(), any(), any())).willReturn(List.of());
    var executedRightAfterTheFirstRead = new AtomicBoolean();
    given(rebalanceTrades.sentRebalanceOrders(TUV100, effectiveDate, pastTheDeadline))
        .willAnswer(
            read ->
                executedRightAfterTheFirstRead.getAndSet(true)
                    ? List.of()
                    : List.of(
                        SentRebalanceOrder.of(
                            "NEW_WORLD", tradeDate, LocalDate.of(2026, 12, 3), estonianCalendar)));
    given(rebalanceTrades.firstRebalancingTradeDate(TUV100, effectiveDate, pastTheDeadline))
        .willAnswer(
            read ->
                executedRightAfterTheFirstRead.getAndSet(true)
                    ? Optional.of(tradeDate)
                    : Optional.empty());

    var window = rebalanceWindows.current(TUV100, pastTheDeadline).orElseThrow();

    assertThat(window.closure()).isEqualTo(RebalanceClosure.byRebalancingTrades(tradeDate));
    assertThat(window.state()).isEqualTo(OK);
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
