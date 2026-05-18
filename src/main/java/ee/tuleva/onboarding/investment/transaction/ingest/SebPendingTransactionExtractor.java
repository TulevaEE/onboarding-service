package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class SebPendingTransactionExtractor {

  List<SebPendingTransactionRow> extract(InvestmentReport report) {
    return report.getRawData().stream()
        .filter(SebPendingTransactionExtractor::hasContent)
        .map(SebPendingTransactionRow::fromRawData)
        .toList();
  }

  private static boolean hasContent(java.util.Map<String, Object> raw) {
    return isNonBlank(raw.get("Client ref"))
        || isNonBlank(raw.get("Our ref"))
        || isNonBlank(raw.get("ISIN"));
  }

  private static boolean isNonBlank(Object value) {
    return value != null && !value.toString().trim().isEmpty();
  }
}
