package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SebPendingTransactionExtractor {

  // A content row that fails strict parsing is dropped from the typed rows but still counted as
  // malformed. Settlement-by-absence must not run on an incomplete report: a dropped order would
  // look "absent" and be falsely settled (see detectSettlementsByAbsence).
  record ExtractionResult(List<SebPendingTransactionRow> rows, int malformedCount) {
    boolean isComplete() {
      return malformedCount == 0;
    }
  }

  ExtractionResult extractWithDiagnostics(InvestmentReport report) {
    List<SebPendingTransactionRow> rows = new ArrayList<>();
    int malformedCount = 0;
    for (Map<String, Object> raw : report.getRawData()) {
      if (!hasContent(raw)) {
        continue;
      }
      try {
        rows.add(SebPendingTransactionRow.fromRawData(raw));
      } catch (RuntimeException e) {
        malformedCount++;
        log.warn(
            "Skipping malformed SEB pending transaction row: clientRef={}, ourRef={}, isin={},"
                + " reason={}",
            raw.get("Client ref"),
            raw.get("Our ref"),
            raw.get("ISIN"),
            e.getMessage());
      }
    }
    return new ExtractionResult(List.copyOf(rows), malformedCount);
  }

  List<SebPendingTransactionRow> extract(InvestmentReport report) {
    return extractWithDiagnostics(report).rows();
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
