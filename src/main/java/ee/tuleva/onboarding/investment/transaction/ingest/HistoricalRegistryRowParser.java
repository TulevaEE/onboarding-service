package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryValueParser.parseDate;
import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryValueParser.parseDecimal;
import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryValueParser.parseInstant;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HistoricalRegistryRowParser {

  private static final String ORDER_UUID_NAMESPACE = "historical-import:";

  private final InstrumentReferenceService instrumentReferenceService;

  ParsedRow parseRow(int rowNumber, CSVRecord record, char decimalSeparator) {
    String orderId = requireValue(record, "order_id");
    String fundIsin = requireValue(record, "fund_isin");
    String instrumentIsin = requireValue(record, "instrument_isin");
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
    ParsedRow row =
        new ParsedRow(
            rowNumber,
            orderId,
            toOrderUuid(orderId),
            value(record, "transaction_id"),
            resolveFund(fundIsin),
            instrumentIsin,
            parseEnum(
                TransactionType.class,
                requireValue(record, "transaction_type"),
                "transaction_type"),
            resolveInstrumentType(value(record, "instrument_type"), instrumentIsin),
            parseDecimal(value(record, "order_amount"), "order_amount", decimalSeparator),
            parseDecimal(value(record, "order_quantity"), "order_quantity", decimalSeparator),
            parseInstant(value(record, "order_timestamp"), "order_timestamp"),
            orderStatus,
            expectedSettlementDate,
            value(record, "comment"),
            parseInstant(value(record, "execution_timestamp"), "execution_timestamp"),
            parseDecimal(value(record, "executed_quantity"), "executed_quantity", decimalSeparator),
            parseDecimal(value(record, "unit_price"), "unit_price", decimalSeparator),
            parseDecimal(
                value(record, "total_consideration"), "total_consideration", decimalSeparator),
            parseDecimal(
                value(record, "net_settlement_amount"), "net_settlement_amount", decimalSeparator),
            parseDecimal(value(record, "commission_amount"), "commission_amount", decimalSeparator),
            actualSettlementDate);
    requireTerminalStatusData(row);
    return row;
  }

  private static void requireTerminalStatusData(ParsedRow row) {
    if (row.orderStatus() != OrderStatus.EXECUTED && row.orderStatus() != OrderStatus.SETTLED) {
      return;
    }
    if (row.orderTimestamp() == null) {
      throw new RowParseException(
          "Missing value: column=order_timestamp, orderId=" + row.orderId());
    }
    if (!row.hasExecutionData()) {
      throw new RowParseException(
          "Terminal order missing execution data: orderId=" + row.orderId());
    }
    if (row.unitPrice() == null) {
      throw new RowParseException("Missing value: column=unit_price, orderId=" + row.orderId());
    }
    if (row.requiresExecutedQuantity() && row.executedQuantity() == null) {
      throw new RowParseException(
          "Missing value: column=executed_quantity, orderId=" + row.orderId());
    }
    if (row.isFundSubscription() && row.totalConsideration() == null) {
      throw new RowParseException(
          "Missing value: column=total_consideration, orderId=" + row.orderId());
    }
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

  private InstrumentType resolveInstrumentType(
      @Nullable String instrumentTypeValue, String instrumentIsin) {
    if (instrumentTypeValue != null) {
      return parseEnum(InstrumentType.class, instrumentTypeValue, "instrument_type");
    }
    InstrumentReference reference =
        instrumentReferenceService
            .findByIsin(instrumentIsin)
            .orElseThrow(
                () ->
                    new RowParseException("Unknown instrument: instrumentIsin=" + instrumentIsin));
    String referenceInstrumentType = reference.getInstrumentType();
    if (referenceInstrumentType == null) {
      throw new RowParseException(
          "Instrument reference missing instrument type: instrumentIsin=" + instrumentIsin);
    }
    try {
      return InstrumentType.valueOf(referenceInstrumentType.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new RowParseException(
          "Instrument reference has unrecognised instrument type: instrumentIsin="
              + instrumentIsin
              + ", instrumentType="
              + referenceInstrumentType);
    }
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
}
