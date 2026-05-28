package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SebPendingTransactionRowTest {

  @Test
  void fromRawData_parsesAllTypedFieldsForBuy() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE000F60HVH9");
    raw.put("Price", new BigDecimal("4.7255"));
    raw.put("Total", new BigDecimal("70915.58"));
    raw.put("Account", "VP68168");
    raw.put("Our ref", "DLA0799512");
    raw.put("Buy/Sell", "Buy");
    raw.put("Currency", "EUR");
    raw.put("Quantity", new BigDecimal("15007"));
    raw.put("Broker fee", new BigDecimal("0.00"));
    raw.put("Client ref", "bd83f551-8c79-4193-b92b-18e1dfd0bd29");
    raw.put("Trade date", "2026-05-11T10:26:04Z");
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "ICAV Amundi MSCI USA Screened UCITS ETF");
    raw.put("Settlement date", "2026-05-13");
    raw.put("Settlement amount", new BigDecimal("70915.58"));

    SebPendingTransactionRow row = SebPendingTransactionRow.fromRawData(raw);

    assertThat(row.clientRef()).isEqualTo(UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"));
    assertThat(row.ourRef()).isEqualTo("DLA0799512");
    assertThat(row.isin()).isEqualTo("IE000F60HVH9");
    assertThat(row.quantity()).isEqualByComparingTo("15007");
    assertThat(row.price()).isEqualByComparingTo("4.7255");
    assertThat(row.settlementAmount()).isEqualByComparingTo("70915.58");
    assertThat(row.brokerFee()).isEqualByComparingTo("0.00");
    assertThat(row.total()).isEqualByComparingTo("70915.58");
    assertThat(row.side()).isEqualTo(BUY);
    assertThat(row.tradeDate()).isEqualTo(Instant.parse("2026-05-11T10:26:04Z"));
    assertThat(row.settlementDate()).isEqualTo(LocalDate.of(2026, 5, 13));
    assertThat(row.clientName()).isEqualTo("Tuleva Täiendav Kogumisfond");
    assertThat(row.account()).isEqualTo("VP68168");
    assertThat(row.instrumentName()).isEqualTo("ICAV Amundi MSCI USA Screened UCITS ETF");
  }

  @Test
  void fromRawData_parsesSellSide() {
    Map<String, Object> raw = baseRow();
    raw.put("Buy/Sell", "Sell");

    SebPendingTransactionRow row = SebPendingTransactionRow.fromRawData(raw);

    assertThat(row.side()).isEqualTo(SELL);
  }

  @Test
  void fromRawData_parsesSideCaseInsensitively() {
    Map<String, Object> raw = baseRow();
    raw.put("Buy/Sell", "BUY");

    SebPendingTransactionRow row = SebPendingTransactionRow.fromRawData(raw);

    assertThat(row.side()).isEqualTo(BUY);
  }

  @Test
  void fromRawData_handlesMissingClientRefAsNull() {
    Map<String, Object> raw = baseRow();
    raw.remove("Client ref");

    SebPendingTransactionRow row = SebPendingTransactionRow.fromRawData(raw);

    assertThat(row.clientRef()).isNull();
  }

  @Test
  void fromRawData_handlesMissingBrokerFeeAsNull() {
    Map<String, Object> raw = baseRow();
    raw.remove("Broker fee");

    SebPendingTransactionRow row = SebPendingTransactionRow.fromRawData(raw);

    assertThat(row.brokerFee()).isNull();
  }

  @Test
  void fromRawData_handlesQuantityAsString() {
    Map<String, Object> raw = baseRow();
    raw.put("Quantity", "2669.9");

    SebPendingTransactionRow row = SebPendingTransactionRow.fromRawData(raw);

    assertThat(row.quantity()).isEqualByComparingTo("2669.9");
  }

  private static Map<String, Object> baseRow() {
    Map<String, Object> raw = new HashMap<>();
    raw.put("ISIN", "IE00BFG1TM61");
    raw.put("Price", new BigDecimal("34.37656841"));
    raw.put("Total", new BigDecimal("91782.00"));
    raw.put("Account", "VP00000");
    raw.put("Our ref", "DLA0000000");
    raw.put("Buy/Sell", "Buy");
    raw.put("Currency", "EUR");
    raw.put("Quantity", new BigDecimal("2669.9"));
    raw.put("Broker fee", null);
    raw.put("Client ref", "00000000-0000-0000-0000-000000000001");
    raw.put("Trade date", "2026-02-10T16:06:58Z");
    raw.put("Client name", "Tuleva Täiendav Kogumisfond");
    raw.put("Instrument name", "iShares Developed World Screened Index Fund");
    raw.put("Settlement date", "2026-02-17");
    raw.put("Settlement amount", new BigDecimal("91782.00"));
    return raw;
  }
}
