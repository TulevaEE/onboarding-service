package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_IN_LINE_DUE_MONTHS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_TRADES_DUE_MONTHS;

import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RebalanceWindows {

  private static final int SISEKORD_4_P_2_6_1_TRADES_DUE_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE = 3;

  private final ModelPortfolioAllocationRepository allocationRepository;
  private final RebalanceTrades rebalanceTrades;
  private final NavWeightsReader navWeightsReader;
  private final InvestmentParameterRepository parameterRepository;
  private final EstonianCalendar estonianCalendar;

  public Optional<RebalanceWindow> current(TulevaFund fund, LocalDate checkDate) {
    return ModelChange.latestIn(changeHistory(fund, checkDate))
        .map(change -> window(fund, change, checkDate, checkDate));
  }

  List<RebalanceWindow> supersededWindows(
      TulevaFund fund, LocalDate checkDate, Predicate<LocalDate> worthMeasuringTheModelOf) {
    var changesNewestFirst = ModelChange.allIn(changeHistory(fund, checkDate));
    return IntStream.range(1, changesNewestFirst.size())
        .filter(
            older -> worthMeasuringTheModelOf.test(changesNewestFirst.get(older).effectiveDate()))
        .mapToObj(
            older ->
                window(
                    fund,
                    changesNewestFirst.get(older),
                    checkDate,
                    changesNewestFirst.get(older - 1).effectiveDate().minusDays(1)))
        .toList();
  }

  private List<ModelPortfolioAllocation> changeHistory(TulevaFund fund, LocalDate checkDate) {
    return allocationRepository.findByFundAndEffectiveDateLessThanEqual(fund, checkDate);
  }

  private RebalanceWindow window(
      TulevaFund fund, ModelChange change, LocalDate checkDate, LocalDate measuredUntil) {
    var tolerance = parameterRepository.findLatestValue(MODEL_REBALANCE_TOLERANCE, checkDate);
    var navs = navWeightsReader.between(fund, change.effectiveDate(), measuredUntil);
    var latest = navs.isEmpty() ? Optional.<NavWeights>empty() : Optional.of(navs.getLast());
    return new RebalanceWindow(
        fund,
        change.effectiveDate(),
        checkDate,
        tolerance,
        change.changedIsins(),
        rebalanceTrades.tradedIsins(fund, change, measuredUntil),
        latest.map(NavWeights::navDate).orElse(null),
        latest.map(nav -> nav.outsideTolerance(change.modelWeights(), tolerance)).orElse(List.of()),
        firstNavInLine(navs, change, tolerance).orElse(null),
        deadlines(change.effectiveDate(), checkDate));
  }

  private static Optional<LocalDate> firstNavInLine(
      List<NavWeights> navs, ModelChange change, BigDecimal tolerance) {
    return navs.stream()
        .filter(nav -> nav.outsideTolerance(change.modelWeights(), tolerance).isEmpty())
        .map(NavWeights::navDate)
        .findFirst();
  }

  private RebalanceDeadlines deadlines(LocalDate effectiveDate, LocalDate checkDate) {
    return new RebalanceDeadlines(
        effectiveDate.plusMonths(months(MODEL_REBALANCE_TRADES_DUE_MONTHS, checkDate)),
        effectiveDate.plusMonths(months(MODEL_REBALANCE_IN_LINE_DUE_MONTHS, checkDate)),
        estonianCalendar.nextOrSameBusinessDay(
            effectiveDate.plusMonths(
                SISEKORD_4_P_2_6_1_TRADES_DUE_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE)));
  }

  private long months(InvestmentParameter parameter, LocalDate checkDate) {
    return parameterRepository.findLatestValue(parameter, checkDate).longValueExact();
  }
}
