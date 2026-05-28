package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

record SebPendingTransactionRow(
    UUID clientRef,
    String ourRef,
    String isin,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal settlementAmount,
    BigDecimal brokerFee,
    BigDecimal total,
    TransactionType side,
    Instant tradeDate,
    LocalDate settlementDate,
    String clientName,
    String account,
    String instrumentName) {

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

  private static UUID parseUuid(Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return UUID.fromString(s);
  }

  private static String asString(Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    return s.isEmpty() ? null : s;
  }

  private static BigDecimal asBigDecimal(Object value) {
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

  private static TransactionType parseSide(Object value) {
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

  private static Instant parseInstant(Object value) {
    if (value == null) {
      return null;
    }
    String s = value.toString().trim();
    if (s.isEmpty()) {
      return null;
    }
    return Instant.parse(s);
  }

  private static LocalDate parseLocalDate(Object value) {
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
