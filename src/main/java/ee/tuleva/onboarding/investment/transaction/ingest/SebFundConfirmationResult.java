package ee.tuleva.onboarding.investment.transaction.ingest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

record SebFundConfirmationResult(
    ReportStatus reportStatus,
    List<String> reportDifferences,
    RegistryComparison registry,
    NavStatus navStatus,
    @Nullable BigDecimal nav,
    @Nullable BigDecimal deviationPercent,
    TimingStatus timingStatus,
    int navDatesAfterReceipt) {

  static final String REGISTRY_MISMATCH = "REGISTRY_MISMATCH";

  enum ReportStatus {
    MATCH,
    MISMATCH,
    NOT_FOUND,
    PENDING
  }

  enum RegistryStatus {
    MATCH,
    MISMATCH,
    NOT_IN_REGISTRY
  }

  record RegistryComparison(RegistryStatus status, List<String> differences) {

    static final RegistryComparison NOT_IN_REGISTRY =
        new RegistryComparison(RegistryStatus.NOT_IN_REGISTRY, List.of());

    static RegistryComparison of(List<String> differences) {
      return new RegistryComparison(
          differences.isEmpty() ? RegistryStatus.MATCH : RegistryStatus.MISMATCH, differences);
    }
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
    if (registryDiffersWhereTheReportAgrees()) {
      findings.add(registryMismatchFinding());
    }
    if (navStatus == NavStatus.FAR_FROM_NAV || navStatus == NavStatus.NOT_PUBLISHED) {
      findings.add(navStatus.name());
    }
    if (timingStatus == TimingStatus.LATE) {
      findings.add(TimingStatus.LATE.name());
    }
    return List.copyOf(findings);
  }

  boolean confirmsTheReportedTrade() {
    return reportStatus == ReportStatus.MATCH;
  }

  private boolean registryDiffersWhereTheReportAgrees() {
    return registry.status() == RegistryStatus.MISMATCH && reportStatus != ReportStatus.MISMATCH;
  }

  String mismatchFinding() {
    return ReportStatus.MISMATCH.name() + " " + String.join("; ", reportDifferences);
  }

  String registryMismatchFinding() {
    return REGISTRY_MISMATCH + " " + String.join("; ", registry.differences());
  }

  List<String> statusSignature() {
    return List.of(reportStatus.name(), navStatus.name(), timingStatus.name());
  }
}
