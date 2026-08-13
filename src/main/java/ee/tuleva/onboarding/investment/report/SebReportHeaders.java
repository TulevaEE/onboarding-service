package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The dates SEB puts above the column header of every report it sends us. Both the positions report
 * and the pending transactions report carry the same five leading lines, so both are read the same
 * way here rather than once per consumer: an "As of" resolved from one report and inferred from the
 * other would put the two sides of a position comparison on different clocks, which is the whole
 * defect this exists to prevent.
 *
 * <p>Current imports find the dates in the report metadata, where {@code
 * SebReportSource#extractCsvMetadata} put them. Reports imported before the header row moved carry
 * those lines as ordinary data rows instead, keyed on the first line's own columns, so the raw rows
 * are searched as a fallback.
 */
@Slf4j
public final class SebReportHeaders {

  private static final String AS_OF_LABEL = "As of:";
  private static final String SENT_LABEL = "Sent:";
  private static final String LABEL_COLUMN = "Fund Management Company:";
  private static final String VALUE_COLUMN = "Tuleva Fondid AS";

  private SebReportHeaders() {}

  public static @Nullable LocalDate asOfDate(InvestmentReport report) {
    return asOfDate(report.getMetadata(), report.getRawData());
  }

  public static @Nullable LocalDate asOfDate(
      Map<String, Object> metadata, List<Map<String, Object>> rawData) {
    return headerDate(metadata, "asOfDate", rawData, AS_OF_LABEL);
  }

  public static @Nullable LocalDate sentDate(
      Map<String, Object> metadata, List<Map<String, Object>> rawData) {
    return headerDate(metadata, "sentDate", rawData, SENT_LABEL);
  }

  private static @Nullable LocalDate headerDate(
      Map<String, Object> metadata,
      String metadataKey,
      List<Map<String, Object>> rawData,
      String headerLabel) {
    LocalDate fromMetadata = parse(metadata.get(metadataKey), metadataKey);
    return fromMetadata == null ? fromRawData(rawData, headerLabel) : fromMetadata;
  }

  private static @Nullable LocalDate fromRawData(
      List<Map<String, Object>> rawData, String headerLabel) {
    for (Map<String, Object> row : rawData) {
      if (headerLabel.equals(string(row.get(LABEL_COLUMN)))) {
        LocalDate parsed = parse(row.get(VALUE_COLUMN), headerLabel);
        if (parsed != null) {
          return parsed;
        }
      }
    }
    return null;
  }

  private static @Nullable LocalDate parse(@Nullable Object value, String source) {
    String text = string(value);
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text);
    } catch (Exception e) {
      log.warn("Failed to parse SEB report header date: source={}, value={}", source, text);
      return null;
    }
  }

  private static @Nullable String string(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }
}
