package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SebPendingTransactionExtractor {

  List<SebPendingTransactionRow> extract(InvestmentReport report) {
    return report.getRawData().stream()
        .filter(SebPendingTransactionExtractor::hasContent)
        .flatMap(SebPendingTransactionExtractor::parseSafely)
        .filter(Objects::nonNull)
        .toList();
  }

  private static Stream<SebPendingTransactionRow> parseSafely(Map<String, Object> raw) {
    try {
      return Stream.of(SebPendingTransactionRow.fromRawData(raw));
    } catch (RuntimeException e) {
      log.warn(
          "Skipping malformed SEB pending transaction row: clientRef={}, ourRef={}, isin={},"
              + " reason={}",
          raw.get("Client ref"),
          raw.get("Our ref"),
          raw.get("ISIN"),
          e.getMessage());
      return Stream.empty();
    }
  }

  private static boolean hasContent(Map<String, Object> raw) {
    return isNonBlank(raw.get("Client ref"))
        || isNonBlank(raw.get("Our ref"))
        || isNonBlank(raw.get("ISIN"));
  }

  private static boolean isNonBlank(Object value) {
    return value != null && !value.toString().trim().isEmpty();
  }
}
