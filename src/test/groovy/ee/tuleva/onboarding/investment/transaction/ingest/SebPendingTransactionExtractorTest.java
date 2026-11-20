package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.investment.report.CsvToJsonConverter;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.SebReportSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class SebPendingTransactionExtractorTest {

  private static final String PREAMBLE =
      "Fund Management Company:;Tuleva Fondid AS;;;;;;;;;;;;;\r\n"
          + "Sent:;2026-11-19;;;;;;;;;;;;;\r\n"
          + "Report type:;Unsettled transactions;;;;;;;;;;;;;\r\n"
          + "As of:;2026-11-18;;;;;;;;;;;;;\r\n";
  private static final String SPACER = ";;;;;;;;;;;;;;\r\n";
  private static final String COLUMNS =
      "Client name;Account;Client ref;Our ref;ISIN;Instrument name;Currency;Quantity;Price;"
          + "Settlement amount;Broker fee;Total;Buy/Sell;Trade date;Settlement date\r\n";
  private static final String TRADE =
      "Tuleva Täiendav Kogumisfond;VP00000;aaaaaaaa-bbbb-cccc-dddd-000000001117;DLA0000000;"
          + "IE000I9HGDZ3;SYNTHETIC TEST SECURITY;EUR;2500;10,00;25000,00;;25000,00;Buy;"
          + "2026-11-17T15:30:00Z;2026-11-19\r\n";
  private static final String EXTRA_LINE = "Page:;1;;;;;;;;;;;;;\r\n";

  private final SebPendingTransactionExtractor extractor = new SebPendingTransactionExtractor();
  private final SebReportSource sebReportSource = new SebReportSource(mock(S3Client.class));

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
  void extractWithDiagnostics_countsMalformedContentRowsAndReportsIncomplete() {
    Map<String, Object> valid =
        Map.of(
            "ISIN", "IE000F60HVH9",
            "Our ref", "DLA0799512",
            "Buy/Sell", "Buy",
            "Quantity", new BigDecimal("15007"),
            "Client ref", "bd83f551-8c79-4193-b92b-18e1dfd0bd29",
            "Trade date", "2026-05-11T10:26:04Z");
    Map<String, Object> malformed = new java.util.HashMap<>();
    malformed.put("ISIN", "IE000F60HVH9");
    malformed.put("Client ref", "not-a-uuid");
    malformed.put("Our ref", "DLA9999999");

    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(valid, malformed))
            .build();

    SebPendingTransactionExtractor.ExtractionResult result =
        extractor.extractWithDiagnostics(report);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.malformedCount()).isEqualTo(1);
    assertThat(result.isComplete()).isFalse();
  }

  @Test
  void extractWithDiagnostics_allRowsValid_reportsComplete() {
    Map<String, Object> valid =
        Map.of(
            "ISIN", "IE000F60HVH9",
            "Our ref", "DLA0799512",
            "Buy/Sell", "Buy",
            "Quantity", new BigDecimal("15007"),
            "Client ref", "bd83f551-8c79-4193-b92b-18e1dfd0bd29",
            "Trade date", "2026-05-11T10:26:04Z");
    InvestmentReport report =
        InvestmentReport.builder()
            .provider(SEB)
            .reportType(PENDING_TRANSACTIONS)
            .reportDate(LocalDate.of(2026, 5, 13))
            .rawData(List.of(valid))
            .build();

    SebPendingTransactionExtractor.ExtractionResult result =
        extractor.extractWithDiagnostics(report);

    assertThat(result.malformedCount()).isZero();
    assertThat(result.isComplete()).isTrue();
  }

  @Test
  void trustedAsOfDate_ofSebsCsv_isItsAsOfDate() {
    InvestmentReport report = reportFromSebCsv(PREAMBLE + SPACER + COLUMNS + TRADE + SPACER);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report))
        .contains(LocalDate.of(2026, 11, 18));
  }

  @Test
  void trustedAsOfDate_ofSebsCsvListingNothingPending_isItsAsOfDate() {
    InvestmentReport report =
        reportFromSebCsv(PREAMBLE + SPACER + COLUMNS + SPACER + SPACER + SPACER + SPACER);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report))
        .contains(LocalDate.of(2026, 11, 18));
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWithAColumnAdded_isItsAsOfDate() {
    InvestmentReport report =
        reportFromSebCsv(
            PREAMBLE
                + SPACER
                + COLUMNS.replace("\r\n", ";Venue\r\n")
                + TRADE.replace("\r\n", ";XOFF\r\n"));

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report))
        .contains(LocalDate.of(2026, 11, 18));
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWithAnExtraLineAboveTheColumnHeader_isEmpty() {
    InvestmentReport report =
        reportFromSebCsv(PREAMBLE + SPACER + EXTRA_LINE + COLUMNS + TRADE + SPACER);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWithAnExtraLineInItsPreamble_isEmpty() {
    InvestmentReport report =
        reportFromSebCsv(PREAMBLE + EXTRA_LINE + SPACER + COLUMNS + TRADE + SPACER);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
  }

  @Test
  void trustedAsOfDate_ofSebsCsvEndingAtItsColumnHeader_isEmpty() {
    InvestmentReport report = reportFromSebCsv(PREAMBLE + SPACER + COLUMNS);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWithItsBuySellColumnRenamed_isEmpty() {
    InvestmentReport report =
        reportFromSebCsv(PREAMBLE + SPACER + COLUMNS.replace("Buy/Sell", "Side") + TRADE);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWithItsQuantityColumnRenamed_isEmpty() {
    InvestmentReport report =
        reportFromSebCsv(PREAMBLE + SPACER + COLUMNS.replace("Quantity", "Units") + TRADE);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWithARowThatCouldNotBeRead_isEmpty() {
    InvestmentReport report =
        reportFromSebCsv(
            PREAMBLE
                + SPACER
                + COLUMNS
                + TRADE
                + TRADE.replace("aaaaaaaa-bbbb-cccc-dddd-000000001117", "not-a-uuid"));

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
  }

  @Test
  void trustedAsOfDate_ofSebsCsvWhoseAsOfDateCannotBeRead_isEmpty() {
    InvestmentReport report =
        reportFromSebCsv(
            PREAMBLE.replace("As of:;2026-11-18", "As of:;18.11.2026") + SPACER + COLUMNS + TRADE);

    assertThat(extractor.extractWithDiagnostics(report).trustedAsOfDate(report)).isEmpty();
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

  private InvestmentReport reportFromSebCsv(String csv) {
    byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
    return InvestmentReport.builder()
        .provider(SEB)
        .reportType(PENDING_TRANSACTIONS)
        .reportDate(LocalDate.of(2026, 11, 19))
        .metadata(sebReportSource.extractCsvMetadata(bytes))
        .rawData(
            new CsvToJsonConverter()
                .convert(new ByteArrayInputStream(bytes), ';', sebReportSource.getHeaderRowIndex()))
        .build();
  }
}
