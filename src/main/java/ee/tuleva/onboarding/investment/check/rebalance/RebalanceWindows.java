package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_WARNING_CALENDAR_DAYS;

import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
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

  private final ModelPortfolioAllocationRepository allocationRepository;
  private final RebalanceTrades rebalanceTrades;
  private final NavWeightsReader navWeightsReader;
  private final InvestmentParameterRepository parameterRepository;
  private final EstonianCalendar estonianCalendar;

  public Optional<RebalanceWindow> current(TulevaFund fund, LocalDate checkDate) {
    return of(fund, checkDate, modelEffectiveDate -> false).current();
  }

  FundRebalanceWindows of(
      TulevaFund fund, LocalDate checkDate, Predicate<LocalDate> stillAnnouncedOpen) {
    var versions =
        ModelVersions.of(
            allocationRepository.findByFundAndEffectiveDateLessThanEqual(fund, checkDate));
    var changesNewestFirst = versions.changesNewestFirst();
    if (changesNewestFirst.isEmpty()) {
      return FundRebalanceWindows.NONE;
    }
    var rules = rulesAsOf(checkDate);
    return new FundRebalanceWindows(
        Optional.of(window(fund, changesNewestFirst.getFirst(), checkDate, checkDate, rules)),
        IntStream.range(1, changesNewestFirst.size())
            .filter(older -> stillAnnouncedOpen.test(changesNewestFirst.get(older).effectiveDate()))
            .mapToObj(
                older -> {
                  var replacedOn = changesNewestFirst.get(older - 1).effectiveDate();
                  return new SupersededWindow(
                      window(
                          fund,
                          changesNewestFirst.get(older),
                          checkDate,
                          replacedOn.minusDays(1),
                          rules),
                      replacedOn);
                })
            .toList());
  }

  private RebalanceRules rulesAsOf(LocalDate checkDate) {
    return new RebalanceRules(
        parameterRepository.findLatestValue(MODEL_REBALANCE_TOLERANCE, checkDate),
        parameterRepository
            .findLatestValue(MODEL_REBALANCE_WARNING_CALENDAR_DAYS, checkDate)
            .longValueExact());
  }

  private RebalanceWindow window(
      TulevaFund fund,
      ModelChange change,
      LocalDate checkDate,
      LocalDate measuredUntil,
      RebalanceRules rules) {
    var navs = navWeightsReader.between(fund, change.effectiveDate(), measuredUntil);
    var latest = navs.isEmpty() ? Optional.<NavWeights>empty() : Optional.of(navs.getLast());
    var tolerance = rules.tolerance();
    var deadline = RebalanceRules.deadlineFrom(change.effectiveDate(), estonianCalendar);
    return new RebalanceWindow(
        fund,
        change.effectiveDate(),
        checkDate,
        tolerance,
        latest.map(NavWeights::navDate).orElse(null),
        latest
            .map(nav -> nav.outsideTolerance(change.weightsOn(nav.navDate()), tolerance))
            .orElse(List.of()),
        RebalanceClosure.whicheverFirst(
                firstNavInLine(navs, change, tolerance),
                rebalanceTrades.firstRebalancingTradeDate(
                    fund, change.effectiveDate(), measuredUntil))
            .orElse(null),
        rebalanceTrades.sentRebalanceOrders(fund, change.effectiveDate(), measuredUntil),
        deadline,
        rules.warningFrom(deadline));
  }

  private static Optional<LocalDate> firstNavInLine(
      List<NavWeights> navs, ModelChange change, BigDecimal tolerance) {
    return navs.stream()
        .filter(nav -> nav.outsideTolerance(change.weightsOn(nav.navDate()), tolerance).isEmpty())
        .map(NavWeights::navDate)
        .findFirst();
  }
}
