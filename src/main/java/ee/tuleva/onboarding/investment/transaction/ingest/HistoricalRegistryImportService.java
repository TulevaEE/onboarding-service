package ee.tuleva.onboarding.investment.transaction.ingest;

import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.BatchStatus;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportFormatException;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult;
import ee.tuleva.onboarding.investment.transaction.HistoricalImportResult.RowError;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionBatch;
import ee.tuleva.onboarding.investment.transaction.TransactionBatchRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@NullMarked
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalRegistryImportService {

  static final List<String> REQUIRED_HEADERS =
      List.of(
          "order_id",
          "fund_isin",
          "instrument_isin",
          "order_timestamp",
          "order_status",
          "expected_settlement_date",
          "comment");

  static final String SOURCE_HISTORICAL_IMPORT = "HISTORICAL_IMPORT";
  static final String CREATED_BY_HISTORICAL_IMPORT = "historical-import";

  private static final String ORDER_UUID_NAMESPACE = "historical-import:";
  private static final DateTimeFormatter SHEET_TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final TransactionBatchRepository batchRepository;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementService settlementService;
  private final Clock clock;

  @Transactional
  public HistoricalImportResult importCsv(String csv) {
    List<CSVRecord> records = parseRecords(csv);
    List<RowError> errors = new ArrayList<>();
    List<ParsedRow> parsedRows = parseRows(records, errors);

    Map<TulevaFund, BigDecimal> totalAmountByFund = totalAmountByFund(parsedRows);
    if (!errors.isEmpty()) {
      return abortedResult(records.size(), errors, totalAmountByFund);
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
      return abortedResult(records.size(), errors, totalAmountByFund);
    }

    int executionsCreated = 0;
    int settlementsCreated = 0;
    Map<TulevaFund, TransactionBatch> batchesByFund = new HashMap<>();
    for (ParsedRow row : rowsToCreate) {
      TransactionBatch batch = batchesByFund.computeIfAbsent(row.fund(), this::createImportBatch);
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
        records.size(),
        rowsToCreate.size(),
        executionsCreated,
        settlementsCreated,
        skippedExisting);

    return new HistoricalImportResult(
        records.size(),
        rowsToCreate.size(),
        executionsCreated,
        settlementsCreated,
        skippedExisting,
        List.of(),
        totalAmountByFund);
  }

  private List<CSVRecord> parseRecords(String csv) {
    char delimiter = sniffDelimiter(csv);
    try (CSVParser parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setDelimiter(delimiter)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .get()
            .parse(new StringReader(csv))) {
      List<String> headers = parser.getHeaderNames().stream().map(this::normalize).toList();
      List<String> missingHeaders =
          REQUIRED_HEADERS.stream().filter(required -> !headers.contains(required)).toList();
      if (!missingHeaders.isEmpty()) {
        throw new HistoricalImportFormatException(missingHeaders, REQUIRED_HEADERS);
      }
      return parser.getRecords();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse historical registry CSV", e);
    }
  }

  private char sniffDelimiter(String csv) {
    String headerLine = csv.lines().findFirst().orElse("");
    return headerLine.contains(";") ? ';' : ',';
  }

  private String normalize(String header) {
    return header.strip().toLowerCase();
  }

  private List<ParsedRow> parseRows(List<CSVRecord> records, List<RowError> errors) {
    List<ParsedRow> parsedRows = new ArrayList<>();
    Set<UUID> seenOrderUuids = new HashSet<>();
    for (CSVRecord record : records) {
      int rowNumber = (int) record.getRecordNumber() + 1;
      try {
        ParsedRow row = parseRow(rowNumber, record);
        if (!seenOrderUuids.add(row.orderUuid())) {
          throw new RowParseException("Duplicate order_id in file: orderId=" + row.orderId());
        }
        parsedRows.add(row);
      } catch (RowParseException e) {
        errors.add(new RowError(rowNumber, e.getMessage()));
      }
    }
    return parsedRows;
  }

  private ParsedRow parseRow(int rowNumber, CSVRecord record) {
    String orderId = requireValue(record, "order_id");
    String fundIsin = requireValue(record, "fund_isin");
    OrderStatus orderStatus =
        parseEnum(OrderStatus.class, requireValue(record, "order_status"), "order_status");
    LocalDate expectedSettlementDate =
        parseDate(value(record, "expected_settlement_date"), "expected_settlement_date");
    LocalDate actualSettlementDate =
        parseDate(value(record, "actual_settlement_date"), "actual_settlement_date");
    if (orderStatus == OrderStatus.SETTLED
        && actualSettlementDate == null
        && expectedSettlementDate == null) {
      throw new RowParseException(
          "Settled row missing both actual and expected settlement date: orderId=" + orderId);
    }
    return new ParsedRow(
        rowNumber,
        orderId,
        toOrderUuid(orderId),
        value(record, "transaction_id"),
        resolveFund(fundIsin),
        requireValue(record, "instrument_isin"),
        parseEnum(
            TransactionType.class, requireValue(record, "transaction_type"), "transaction_type"),
        parseInstrumentType(value(record, "instrument_type")),
        parseDecimal(value(record, "order_amount"), "order_amount"),
        parseDecimal(value(record, "order_quantity"), "order_quantity"),
        parseInstant(value(record, "order_timestamp"), "order_timestamp"),
        orderStatus,
        expectedSettlementDate,
        value(record, "comment"),
        parseInstant(value(record, "execution_timestamp"), "execution_timestamp"),
        parseDecimal(value(record, "executed_quantity"), "executed_quantity"),
        parseDecimal(value(record, "unit_price"), "unit_price"),
        parseDecimal(value(record, "total_consideration"), "total_consideration"),
        parseDecimal(value(record, "net_settlement_amount"), "net_settlement_amount"),
        parseDecimal(value(record, "commission_amount"), "commission_amount"),
        actualSettlementDate);
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
        .actualSettlementDate(row.actualSettlementDate())
        .source(SOURCE_HISTORICAL_IMPORT)
        .modifiedBy(CREATED_BY_HISTORICAL_IMPORT)
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

  private static UUID toOrderUuid(String orderId) {
    try {
      return UUID.fromString(orderId);
    } catch (IllegalArgumentException e) {
      return UUID.nameUUIDFromBytes((ORDER_UUID_NAMESPACE + orderId).getBytes(UTF_8));
    }
  }

  private static TulevaFund resolveFund(String fundIsin) {
    return TulevaFund.findByIsin(fundIsin)
        .orElseThrow(() -> new RowParseException("Unknown fund: fundIsin=" + fundIsin));
  }

  private static InstrumentType parseInstrumentType(@Nullable String value) {
    if (value == null) {
      return InstrumentType.ETF;
    }
    return parseEnum(InstrumentType.class, value, "instrument_type");
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String column) {
    try {
      return Enum.valueOf(type, value.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new RowParseException("Invalid value: column=" + column + ", value=" + value);
    }
  }

  private static @Nullable String value(CSVRecord record, String column) {
    if (!record.isMapped(column)) {
      return null;
    }
    String value = record.get(column);
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String requireValue(CSVRecord record, String column) {
    String value = value(record, column);
    if (value == null) {
      throw new RowParseException("Missing value: column=" + column);
    }
    return value;
  }

  private static @Nullable BigDecimal parseDecimal(@Nullable String raw, String column) {
    if (raw == null) {
      return null;
    }
    String cleaned = raw.replace(" ", "").replace(" ", "");
    if (cleaned.contains(",") && cleaned.contains(".")) {
      cleaned =
          cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
              ? cleaned.replace(".", "").replace(',', '.')
              : cleaned.replace(",", "");
    } else {
      cleaned = cleaned.replace(',', '.');
    }
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      throw new RowParseException("Invalid number: column=" + column + ", value=" + raw);
    }
  }

  private static @Nullable Instant parseInstant(@Nullable String raw, String column) {
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ignored) {
      // not ISO instant — try sheet formats below
    }
    try {
      return LocalDateTime.parse(raw, SHEET_TIMESTAMP).toInstant(UTC);
    } catch (DateTimeParseException ignored) {
      // not a sheet datetime — try ISO local datetime
    }
    try {
      return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(UTC);
    } catch (DateTimeParseException ignored) {
      // not a datetime at all — fall back to date-only
    }
    LocalDate date = parseDate(raw, column);
    return date == null ? null : date.atStartOfDay(UTC).toInstant();
  }

  private static @Nullable LocalDate parseDate(@Nullable String raw, String column) {
    if (raw == null) {
      return null;
    }
    try {
      return LocalDate.parse(raw);
    } catch (DateTimeParseException ignored) {
      // not ISO date — try Estonian format below
    }
    try {
      return LocalDate.parse(raw, ESTONIAN_DATE);
    } catch (DateTimeParseException e) {
      throw new RowParseException("Invalid date: column=" + column + ", value=" + raw);
    }
  }

  private record ParsedRow(
      int rowNumber,
      String orderId,
      UUID orderUuid,
      @Nullable String brokerTransactionId,
      TulevaFund fund,
      String instrumentIsin,
      TransactionType transactionType,
      InstrumentType instrumentType,
      @Nullable BigDecimal orderAmount,
      @Nullable BigDecimal orderQuantity,
      @Nullable Instant orderTimestamp,
      OrderStatus orderStatus,
      @Nullable LocalDate expectedSettlementDate,
      @Nullable String comment,
      @Nullable Instant executionTimestamp,
      @Nullable BigDecimal executedQuantity,
      @Nullable BigDecimal unitPrice,
      @Nullable BigDecimal totalConsideration,
      @Nullable BigDecimal settlementAmount,
      @Nullable BigDecimal commissionAmount,
      @Nullable LocalDate actualSettlementDate) {

    boolean hasExecutionData() {
      return executionTimestamp != null
          || executedQuantity != null
          || unitPrice != null
          || totalConsideration != null;
    }

    LocalDate settlementReportDate() {
      if (actualSettlementDate != null) {
        return actualSettlementDate;
      }
      if (expectedSettlementDate != null) {
        return expectedSettlementDate;
      }
      throw new RowParseException(
          "Settled row missing both actual and expected settlement date: orderId=" + orderId);
    }

    BigDecimal totalAmount() {
      if (totalConsideration != null) {
        return totalConsideration;
      }
      return orderAmount != null ? orderAmount : ZERO;
    }
  }

  private static class RowParseException extends RuntimeException {
    RowParseException(String message) {
      super(message);
    }
  }
}
