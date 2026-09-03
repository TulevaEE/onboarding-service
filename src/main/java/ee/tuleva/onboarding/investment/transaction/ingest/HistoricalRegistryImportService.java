package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import ee.tuleva.onboarding.investment.transaction.BatchStatus;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult.RowError;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryCsvParser.ParseResult;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalRegistryImportService {

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  static final String SOURCE_HISTORICAL_IMPORT = "HISTORICAL_IMPORT";
  static final String CREATED_BY_HISTORICAL_IMPORT = "historical-import";

  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final HistoricalRegistryCsvParser csvParser;
  private final Clock clock;

  @Transactional
  public HistoricalImportResult importCsv(String csv) {
    ParseResult parseResult = csvParser.parse(csv);
    List<ParsedRow> parsedRows = parseResult.rows();
    List<RowError> errors = new ArrayList<>(parseResult.errors());

    Map<TulevaFund, BigDecimal> totalAmountByFund = totalAmountByFund(parsedRows);
    if (!errors.isEmpty()) {
      return abortedResult(parseResult.rowCount(), errors, totalAmountByFund);
    }

    List<ParsedRow> rowsToCreate = new ArrayList<>();
    int skippedExisting = 0;
    for (ParsedRow row : parsedRows) {
      if (orderRepository.findByOrderUuid(row.orderUuid()).isPresent()) {
        skippedExisting++;
      } else if (isDuplicateBrokerTransactionId(row)) {
        errors.add(
            new RowError(
                row.rowNumber(),
                "Duplicate brokerTransactionId: brokerTransactionId=" + row.brokerTransactionId()));
      } else {
        rowsToCreate.add(row);
      }
    }
    if (!errors.isEmpty()) {
      return abortedResult(parseResult.rowCount(), errors, totalAmountByFund);
    }

    int executionsCreated = 0;
    int settlementsCreated = 0;
    Map<TulevaFund, TransactionBatch> batchesByFund = new HashMap<>();
    for (ParsedRow row : rowsToCreate) {
      TransactionBatch batch = batchesByFund.get(row.fund());
      if (batch == null) {
        batch = createImportBatch(row.fund());
        batchesByFund.put(row.fund(), batch);
      }
      TransactionOrder order = orderRepository.save(toOrder(row, batch));
      if (row.hasExecutionData()) {
        executionRepository.save(toExecution(row, order));
        executionsCreated++;
      }
      if (row.orderStatus() == OrderStatus.SETTLED) {
        settlementService.recordSettlement(order, row.settlementReportDate());
        settlementsCreated++;
      }
    }

    log.info(
        "Historical registry import completed: rowCount={}, ordersCreated={}, "
            + "executionsCreated={}, settlementsCreated={}, skippedExisting={}",
        parseResult.rowCount(),
        rowsToCreate.size(),
        executionsCreated,
        settlementsCreated,
        skippedExisting);

    return new HistoricalImportResult(
        parseResult.rowCount(),
        rowsToCreate.size(),
        executionsCreated,
        settlementsCreated,
        skippedExisting,
        List.of(),
        totalAmountByFund);
  }

  private boolean isDuplicateBrokerTransactionId(ParsedRow row) {
    return row.brokerTransactionId() != null
        && executionRepository.findByBrokerTransactionId(row.brokerTransactionId()).isPresent();
  }

  private TransactionBatch createImportBatch(TulevaFund fund) {
    return batchRepository.save(
        TransactionBatch.builder()
            .fund(fund)
            .createdBy(CREATED_BY_HISTORICAL_IMPORT)
            .status(BatchStatus.SENT)
            .metadata(
                Map.of(
                    "source", SOURCE_HISTORICAL_IMPORT, "importedAt", clock.instant().toString()))
            .build());
  }

  private TransactionOrder toOrder(ParsedRow row, TransactionBatch batch) {
    return TransactionOrder.builder()
        .batch(batch)
        .fund(row.fund())
        .instrumentIsin(row.instrumentIsin())
        .transactionType(row.transactionType())
        .instrumentType(row.instrumentType())
        .orderAmount(row.orderAmount())
        .orderQuantity(row.orderQuantity())
        .orderVenue(OrderVenue.SEB)
        .orderUuid(row.orderUuid())
        .orderStatus(row.orderStatus())
        .orderTimestamp(row.orderTimestamp())
        .expectedSettlementDate(row.expectedSettlementDate())
        .comment(row.comment())
        .build();
  }

  private TransactionExecution toExecution(ParsedRow row, TransactionOrder order) {
    return TransactionExecution.builder()
        .orderId(order.getId())
        .brokerTransactionId(row.brokerTransactionId())
        .executionTimestamp(row.executionTimestamp())
        .executedQuantity(row.executedQuantity())
        .unitPrice(row.unitPrice())
        .totalConsideration(row.totalConsideration())
        .settlementAmount(row.settlementAmount())
        .commissionAmount(row.commissionAmount())
        .scheduledSettlementDate(row.actualSettlementDate())
        .reportedDate(reportedDate(row))
        .source(SOURCE_HISTORICAL_IMPORT)
        .modifiedBy(CREATED_BY_HISTORICAL_IMPORT)
        .build();
  }

  private @Nullable LocalDate reportedDate(ParsedRow row) {
    Instant known =
        row.executionTimestamp() == null ? row.orderTimestamp() : row.executionTimestamp();
    return known == null ? row.actualSettlementDate() : LocalDate.ofInstant(known, TALLINN);
  }

  private HistoricalImportResult abortedResult(
      int rowCount, List<RowError> errors, Map<TulevaFund, BigDecimal> totalAmountByFund) {
    log.warn("Historical registry import aborted: rowCount={}, errors={}", rowCount, errors.size());
    return new HistoricalImportResult(rowCount, 0, 0, 0, 0, List.copyOf(errors), totalAmountByFund);
  }

  private Map<TulevaFund, BigDecimal> totalAmountByFund(List<ParsedRow> parsedRows) {
    Map<TulevaFund, BigDecimal> totals = new LinkedHashMap<>();
    parsedRows.forEach(row -> totals.merge(row.fund(), row.totalAmount(), BigDecimal::add));
    return totals;
  }
}
