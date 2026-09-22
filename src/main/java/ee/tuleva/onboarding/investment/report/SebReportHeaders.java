package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Slf4j
public final class SebReportHeaders {

  static final String AS_OF_LABEL = "As of:";
  static final String SENT_LABEL = "Sent:";
  static final String AS_OF_METADATA_KEY = "asOfDate";
  static final String SENT_METADATA_KEY = "sentDate";
  private static final String LABEL_COLUMN = "Fund Management Company:";
  private static final String VALUE_COLUMN = "Tuleva Fondid AS";

  private SebReportHeaders() {}

  public static @Nullable LocalDate asOfDate(InvestmentReport report) {
    return asOfDate(report.getMetadata(), report.getRawData());
  }

  public static @Nullable LocalDate asOfDate(
      Map<String, Object> metadata, List<Map<String, Object>> rawData) {
    return headerDate(metadata, AS_OF_METADATA_KEY, rawData, AS_OF_LABEL);
  }

  public static @Nullable LocalDate sentDate(
      Map<String, Object> metadata, List<Map<String, Object>> rawData) {
    return headerDate(metadata, SENT_METADATA_KEY, rawData, SENT_LABEL);
  }

  public static @Nullable String unreadableAsOfValue(
      Map<String, Object> metadata, List<Map<String, Object>> rawData) {
    if (asOfDate(metadata, rawData) != null) {
      return null;
    }
    String fromMetadata = string(metadata.get(AS_OF_METADATA_KEY));
    if (fromMetadata != null) {
      return fromMetadata;
    }
    return rawData.stream()
        .filter(row -> AS_OF_LABEL.equals(string(row.get(LABEL_COLUMN))))
        .map(row -> string(row.get(VALUE_COLUMN)))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
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
