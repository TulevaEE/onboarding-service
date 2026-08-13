package ee.tuleva.onboarding.investment.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SebReportHeadersTest {

  private static final LocalDate AS_OF = LocalDate.of(2026, 1, 25);
  private static final LocalDate SENT = LocalDate.of(2026, 1, 26);

  @Test
  void asOfDate_readsMetadata() {
    Map<String, Object> metadata = Map.of("asOfDate", "2026-01-25", "sentDate", "2026-01-26");

    assertThat(SebReportHeaders.asOfDate(metadata, List.of())).isEqualTo(AS_OF);
    assertThat(SebReportHeaders.sentDate(metadata, List.of())).isEqualTo(SENT);
  }

  @Test
  void asOfDate_fallsBackToRawDataHeaderRows() {
    List<Map<String, Object>> rawData = List.of(headerRow("As of:", "2026-01-25"), dataRow());

    assertThat(SebReportHeaders.asOfDate(Map.of(), rawData)).isEqualTo(AS_OF);
  }

  @Test
  void sentDate_fallsBackToRawDataHeaderRows() {
    List<Map<String, Object>> rawData = List.of(dataRow(), headerRow("Sent:", "2026-01-26"));

    assertThat(SebReportHeaders.sentDate(Map.of(), rawData)).isEqualTo(SENT);
  }

  @Test
  void asOfDate_metadataTakesPrecedenceOverRawData() {
    List<Map<String, Object>> rawData = List.of(headerRow("As of:", "2020-01-01"));

    assertThat(SebReportHeaders.asOfDate(Map.of("asOfDate", "2026-01-25"), rawData))
        .isEqualTo(AS_OF);
  }

  @Test
  void asOfDate_readsFromInvestmentReport() {
    InvestmentReport report =
        InvestmentReport.builder()
            .reportDate(SENT)
            .metadata(Map.of("asOfDate", "2026-01-25"))
            .rawData(List.of())
            .build();

    assertThat(SebReportHeaders.asOfDate(report)).isEqualTo(AS_OF);
  }

  @Test
  void asOfDate_isNullWhenNothingCarriesIt() {
    assertThat(SebReportHeaders.asOfDate(Map.of(), List.of(dataRow()))).isNull();
    assertThat(SebReportHeaders.sentDate(Map.of(), List.of(dataRow()))).isNull();
  }

  @Test
  void asOfDate_isNullWhenMetadataDateIsUnparseable() {
    assertThat(SebReportHeaders.asOfDate(Map.of("asOfDate", "26.01.2026"), List.of())).isNull();
  }

  @Test
  void asOfDate_fallsBackToRawDataWhenMetadataDateIsUnparseable() {
    List<Map<String, Object>> rawData = List.of(headerRow("As of:", "2026-01-25"));

    assertThat(SebReportHeaders.asOfDate(Map.of("asOfDate", "not a date"), rawData))
        .isEqualTo(AS_OF);
  }

  @Test
  void asOfDate_isNullWhenHeaderRowCarriesAnUnparseableDate() {
    assertThat(SebReportHeaders.asOfDate(Map.of(), List.of(headerRow("As of:", "25/01/2026"))))
        .isNull();
  }

  @Test
  void asOfDate_keepsLookingPastAHeaderRowThatCarriesNoDate() {
    List<Map<String, Object>> rawData =
        List.of(headerRow("As of:", "  "), headerRow("As of:", "2026-01-25"));

    assertThat(SebReportHeaders.asOfDate(Map.of(), rawData)).isEqualTo(AS_OF);
  }

  @Test
  void asOfDate_treatsBlankAndNullMetadataValuesAsAbsent() {
    Map<String, Object> blank = new HashMap<>();
    blank.put("asOfDate", "   ");
    blank.put("sentDate", null);

    assertThat(SebReportHeaders.asOfDate(blank, List.of())).isNull();
    assertThat(SebReportHeaders.sentDate(blank, List.of())).isNull();
  }

  @Test
  void asOfDate_trimsSurroundingWhitespace() {
    assertThat(SebReportHeaders.asOfDate(Map.of("asOfDate", " 2026-01-25 "), List.of()))
        .isEqualTo(AS_OF);
  }

  private static Map<String, Object> headerRow(String label, String value) {
    Map<String, Object> row = new HashMap<>();
    row.put("Fund Management Company:", label);
    row.put("Tuleva Fondid AS", value);
    return row;
  }

  private static Map<String, Object> dataRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("Fund Management Company:", "TKF100");
    row.put("Tuleva Fondid AS", "Cash account in SEB Pank");
    return row;
  }
}
