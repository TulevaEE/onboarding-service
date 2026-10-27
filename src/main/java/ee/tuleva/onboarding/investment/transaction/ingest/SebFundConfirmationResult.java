package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

record SebFundConfirmationResult(
    ReportStatus reportStatus,
    List<String> reportDifferences,
    NavStatus navStatus,
    @Nullable BigDecimal nav,
    @Nullable BigDecimal deviationPercent,
    TimingStatus timingStatus,
    int navDatesAfterReceipt) {

  enum ReportStatus {
    MATCH,
    MISMATCH,
    NOT_FOUND,
    PENDING
  }

  enum NavStatus {
    WITHIN_TOLERANCE,
    FAR_FROM_NAV,
    NOT_PUBLISHED_YET,
    NOT_PUBLISHED
  }

  enum TimingStatus {
    ON_TIME,
    LATE
  }

  boolean isActionable() {
    return !findings().isEmpty();
  }

  List<String> findings() {
    List<String> findings = new ArrayList<>();
    if (reportStatus == ReportStatus.MISMATCH) {
      findings.add(mismatchFinding());
    }
    if (reportStatus == ReportStatus.NOT_FOUND) {
      findings.add(ReportStatus.NOT_FOUND.name());
    }
    if (navStatus == NavStatus.FAR_FROM_NAV || navStatus == NavStatus.NOT_PUBLISHED) {
      findings.add(navStatus.name());
    }
    if (timingStatus == TimingStatus.LATE) {
      findings.add(TimingStatus.LATE.name());
    }
    return List.copyOf(findings);
  }

  String mismatchFinding() {
    return ReportStatus.MISMATCH.name() + " " + String.join("; ", reportDifferences);
  }

  List<String> statusSignature() {
    return List.of(reportStatus.name(), navStatus.name(), timingStatus.name());
  }
}
