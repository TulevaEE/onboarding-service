package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_IN_LINE_DUE_MONTHS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_TOLERANCE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MODEL_REBALANCE_TRADES_DUE_MONTHS;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RebalanceWindows {

  private static final int SISEKORD_4_P_2_6_1_TRADES_DUE_WITHIN_MONTHS_OF_THE_EFFECTIVE_DATE = 3;
  private static final Set<OrderStatus> PLACED_ORDERS = EnumSet.of(SENT, EXECUTED, SETTLED);
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final ModelPortfolioAllocationRepository allocationRepository;
  private final TransactionOrderRepository orderRepository;
  private final NavWeightsReader navWeightsReader;
  private final InvestmentParameterRepository parameterRepository;
  private final EstonianCalendar estonianCalendar;

  public Optional<RebalanceWindow> current(TulevaFund fund, LocalDate checkDate) {
    return ModelChange.latestIn(
            allocationRepository.findByFundAndEffectiveDateLessThanEqual(fund, checkDate))
        .map(change -> window(fund, change, checkDate));
  }

  private RebalanceWindow window(TulevaFund fund, ModelChange change, LocalDate checkDate) {
    var tolerance = parameterRepository.findLatestValue(MODEL_REBALANCE_TOLERANCE, checkDate);
    var navs = navWeightsReader.between(fund, change.effectiveDate(), checkDate);
    var latest = navs.isEmpty() ? Optional.<NavWeights>empty() : Optional.of(navs.getLast());
    return new RebalanceWindow(
        fund,
        change.effectiveDate(),
        checkDate,
        tolerance,
        change.changedIsins(),
        tradedIsins(fund, change, checkDate),
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

  private Set<String> tradedIsins(TulevaFund fund, ModelChange change, LocalDate checkDate) {
    return orderRepository
        .findPlacedBetween(
            fund,
            PLACED_ORDERS,
            change.effectiveDate().atStartOfDay(TALLINN).toInstant(),
            checkDate.plusDays(1).atStartOfDay(TALLINN).toInstant())
        .stream()
        .filter(RebalanceWindows::knownToHaveGoneOut)
        .map(TransactionOrder::getInstrumentIsin)
        .filter(change.changedIsins()::contains)
        .collect(toUnmodifiableSet());
  }

  private static boolean knownToHaveGoneOut(TransactionOrder order) {
    return order.getOrderStatus() != SENT || order.getBatch().importedFromTheTradeRegistry();
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
