package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SebPendingTransactionExtractorTest {

  private final SebPendingTransactionExtractor extractor = new SebPendingTransactionExtractor();

  @Test
  void extract_mapsEachRawDataRowToTypedRecord() {
    Map<String, Object> raw1 =
        Map.of(
            "ISIN", "IE000F60HVH9",
            "Price", new BigDecimal("4.7255"),
            "Total", new BigDecimal("70915.58"),
            "Account", "VP68168",
            "Our ref", "DLA0799512",
            "Buy/Sell", "Buy",
            "Quantity", new BigDecimal("15007"),
            "Client ref", "bd83f551-8c79-4193-b92b-18e1dfd0bd29",
            "Trade date", "2026-05-11T10:26:04Z",
            "Settlement date", "2026-05-13");
    Map<String, Object> raw2 =
        Map.of(
            "ISIN", "IE00BFG1TM61",
            "Price", new BigDecimal("34.37"),
            "Total", new BigDecimal("91782.00"),
            "Account", "VP68958",
            "Our ref", "DLA0000001",
            "Buy/Sell", "Sell",
            "Quantity", new BigDecimal("2669.9"),
            "Client ref", "00000000-0000-0000-0000-000000000002",
            "Trade date", "2026-02-10T16:06:58Z",
            "Settlement date", "2026-02-17");

    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(raw1, raw2))
            .build();

    List<SebPendingTransactionRow> rows = extractor.extract(report);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).clientRef())
        .isEqualTo(UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29"));
    assertThat(rows.get(0).ourRef()).isEqualTo("DLA0799512");
    assertThat(rows.get(1).ourRef()).isEqualTo("DLA0000001");
  }

  @Test
  void extract_skipsBlankPaddingRowsWithoutClientRefOurRefIsin() {
    Map<String, Object> blank = new java.util.HashMap<>();
    blank.put("Client ref", null);
    blank.put("Our ref", null);
    blank.put("ISIN", "");
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(blank))
            .build();

    assertThat(extractor.extract(report)).isEmpty();
  }

  @Test
  void extract_emptyRawData_returnsEmptyList() {
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of())
            .build();

    assertThat(extractor.extract(report)).isEmpty();
  }
}
