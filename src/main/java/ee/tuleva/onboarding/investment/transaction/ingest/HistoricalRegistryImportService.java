package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.SettlementSource.HISTORICAL_IMPORT;

import ee.tuleva.onboarding.investment.transaction.BatchStatus;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult.RowError;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryCsvParser.ParseResult;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalRegistryImportService {

  static final String SOURCE_HISTORICAL_IMPORT = "HISTORICAL_IMPORT";
  static final String CREATED_BY_HISTORICAL_IMPORT = "historical-import";

  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final HistoricalRegistryCsvParser csvParser;
  private final HistoricalExecutionWriter executionWriter;
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
        executionWriter.write(row, order);
        executionsCreated++;
      }
      if (row.orderStatus() == OrderStatus.SETTLED) {
        settlementService.recordSettlement(order, row.settlementReportDate(), HISTORICAL_IMPORT);
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
