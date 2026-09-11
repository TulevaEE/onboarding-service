package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record SebPendingTransactionRow(
    @Nullable UUID clientRef,
    @Nullable String ourRef,
    @Nullable String isin,
    @Nullable BigDecimal quantity,
    @Nullable BigDecimal price,
    @Nullable BigDecimal settlementAmount,
    @Nullable BigDecimal brokerFee,
    @Nullable BigDecimal total,
    @Nullable TransactionType side,
    @Nullable Instant tradeDate,
    @Nullable LocalDate settlementDate,
    @Nullable String clientName,
    @Nullable String account,
    @Nullable String instrumentName) {

  static SebPendingTransactionRow fromRawData(Map<String, Object> raw) {
    return new SebPendingTransactionRow(
        parseUuid(raw.get("Client ref")),
        asString(raw.get("Our ref")),
        asString(raw.get("ISIN")),
        asBigDecimal(raw.get("Quantity")),
        asBigDecimal(raw.get("Price")),
        asBigDecimal(raw.get("Settlement amount")),
        asBigDecimal(raw.get("Broker fee")),
        asBigDecimal(raw.get("Total")),
        parseSide(raw.get("Buy/Sell")),
        parseInstant(raw.get("Trade date")),
        parseLocalDate(raw.get("Settlement date")),
        asString(raw.get("Client name")),
        asString(raw.get("Account")),
        asString(raw.get("Instrument name")));
  }

  private static @Nullable UUID parseUuid(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return UUID.fromString(s);
  }

  private static @Nullable String asString(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    return s.isEmpty() ? null : s;
  }

  private static @Nullable BigDecimal asBigDecimal(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return new BigDecimal(s);
  }

  private static @Nullable TransactionType parseSide(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim().toUpperCase();
    return switch (s) {
      case "BUY" -> TransactionType.BUY;
      case "SELL" -> TransactionType.SELL;
      default -> throw new IllegalArgumentException("Unknown Buy/Sell value: value=" + value);
    };
  }

  private static @Nullable Instant parseInstant(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return Instant.parse(s);
  }

  private static @Nullable LocalDate parseLocalDate(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return LocalDate.parse(s);
  }
}
