package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisCalculator;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisCalculator.ExecutionEvent;
import ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisCalculator.PriorPosition;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioBaseline;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioBaselineEntry;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioBaselineRepository;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasis;
import ee.tuleva.onboarding.investment.transaction.portfolio.PortfolioCostBasisRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioCostBasisService {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final PortfolioCostBasisRepository costBasisRepository;
  private final PortfolioBaselineRepository baselineRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final CostBasisCalculator calculator;

  @Transactional
  public void runForFundAndDate(TulevaFund fund, LocalDate asOfDate) {
    String fundIsin = fund.getIsin();
    Optional<PortfolioBaseline> baselineOpt = baselineRepository.findByFundIsin(fundIsin);
    if (baselineOpt.isEmpty()) {
      log.warn("No baseline for fund: fundCode={}, fundIsin={}", fund.getCode(), fundIsin);
      return;
    }
    PortfolioBaseline baseline = baselineOpt.get();
    if (!asOfDate.isAfter(baseline.getBaselineDate())) {
      log.info(
          "Skipping run: asOfDate={} is not after baselineDate={}, fundIsin={}",
          asOfDate,
          baseline.getBaselineDate(),
          fundIsin);
      return;
    }

    List<TransactionExecution> dayExecutions = findExecutionsByTradeDate(fund, asOfDate);
    Set<String> isins = new HashSet<>(baselineEntryIsins(baseline));
    isins.addAll(executionIsins(dayExecutions));

    for (String isin : isins) {
      computeAndUpsert(fund, asOfDate, isin, baseline, dayExecutions);
    }
  }

  @Transactional
  public void rebuildRange(TulevaFund fund, LocalDate fromDate, LocalDate toDate) {
    LocalDate cursor = fromDate;
    while (!cursor.isAfter(toDate)) {
      runForFundAndDate(fund, cursor);
      cursor = cursor.plusDays(1);
    }
  }

  @Transactional(readOnly = true)
  public List<PortfolioCostBasisSnapshot> snapshotForFundAndDate(
      TulevaFund fund, LocalDate asOfDate) {
    return costBasisRepository.findByFundIsinAndAsOfDate(fund.getIsin(), asOfDate).stream()
        .map(PortfolioCostBasisService::toSnapshot)
        .toList();
  }

  private static PortfolioCostBasisSnapshot toSnapshot(PortfolioCostBasis row) {
    return new PortfolioCostBasisSnapshot(
        row.getInstrumentIsin(),
        row.getQuantity(),
        row.getAvgUnitCost(),
        row.getTotalCost(),
        row.getAsOfDate());
  }

  private void computeAndUpsert(
      TulevaFund fund,
      LocalDate asOfDate,
      String isin,
      PortfolioBaseline baseline,
      List<TransactionExecution> dayExecutions) {
    String fundIsin = fund.getIsin();
    Optional<PriorPosition> prior = priorPosition(baseline, fundIsin, isin, asOfDate);
    List<ExecutionEvent> events = executionsFor(isin, dayExecutions);

    if (prior.isEmpty() && events.isEmpty()) {
      return;
    }

    PortfolioCostBasis computed = calculator.calculate(prior, events, fundIsin, isin, asOfDate);

    Optional<PortfolioCostBasis> existing =
        costBasisRepository.findByFundIsinAndInstrumentIsinAndAsOfDate(fundIsin, isin, asOfDate);
    if (existing.isPresent()) {
      PortfolioCostBasis row = existing.get();
      row.setQuantity(computed.getQuantity());
      row.setAvgUnitCost(computed.getAvgUnitCost());
      row.setTotalCost(computed.getTotalCost());
      row.setDeltaQuantity(computed.getDeltaQuantity());
      row.setSource(computed.getSource());
      costBasisRepository.save(row);
    } else {
      costBasisRepository.save(computed);
    }
  }

  private Optional<PriorPosition> priorPosition(
      PortfolioBaseline baseline, String fundIsin, String isin, LocalDate asOfDate) {
    Optional<PortfolioCostBasis> previousRow =
        costBasisRepository.findLatestByFundIsinAndInstrumentIsinBefore(fundIsin, isin, asOfDate);
    if (previousRow.isPresent()) {
      PortfolioCostBasis row = previousRow.get();
      if (!row.getAsOfDate().isBefore(baseline.getBaselineDate())) {
        return Optional.of(PriorPosition.from(row));
      }
    }
    return baseline.getEntries().stream()
        .filter(e -> e.getInstrumentIsin().equals(isin))
        .findFirst()
        .map(PriorPosition::from);
  }

  private List<TransactionExecution> findExecutionsByTradeDate(TulevaFund fund, LocalDate date) {
    List<Long> fundOrderIds =
        orderRepository.findByFund(fund).stream().map(TransactionOrder::getId).toList();
    if (fundOrderIds.isEmpty()) {
      return List.of();
    }
    Instant fromInclusive = date.atStartOfDay(TALLINN).toInstant();
    Instant toExclusive = date.plusDays(1).atStartOfDay(TALLINN).toInstant();
    return executionRepository
        .findByOrderIdInAndExecutionTimestampInRange(fundOrderIds, fromInclusive, toExclusive)
        .stream()
        .sorted(
            Comparator.comparing(
                    TransactionExecution::getExecutionTimestamp,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    TransactionExecution::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private List<ExecutionEvent> executionsFor(
      String isin, List<TransactionExecution> dayExecutions) {
    return dayExecutions.stream()
        .filter(e -> isinFor(e).equals(isin))
        .map(e -> ExecutionEvent.of(e, sideFor(e)))
        .toList();
  }

  private String isinFor(TransactionExecution execution) {
    return orderRepository
        .findById(execution.getOrderId())
        .map(TransactionOrder::getInstrumentIsin)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Execution refers to missing order: orderId=" + execution.getOrderId()));
  }

  private TransactionType sideFor(TransactionExecution execution) {
    return orderRepository
        .findById(execution.getOrderId())
        .map(TransactionOrder::getTransactionType)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Execution refers to missing order: orderId=" + execution.getOrderId()));
  }

  private static List<String> baselineEntryIsins(PortfolioBaseline baseline) {
    return baseline.getEntries().stream().map(PortfolioBaselineEntry::getInstrumentIsin).toList();
  }

  private List<String> executionIsins(List<TransactionExecution> executions) {
    return executions.stream().map(this::isinFor).distinct().toList();
  }
}
