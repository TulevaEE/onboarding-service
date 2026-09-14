package ee.tuleva.onboarding.investment.report;

import org.jspecify.annotations.Nullable;

public class MissingReportAsOfDateException extends RuntimeException {

  private final transient @Nullable String unreadableValue;

  public MissingReportAsOfDateException(
      ReportProvider provider, ReportType reportType, @Nullable String unreadableValue) {
    super(
        "No usable 'As of' date in report: provider=%s, reportType=%s, unreadableValue=%s"
            .formatted(provider, reportType, unreadableValue));
    this.unreadableValue = unreadableValue;
  }

  public @Nullable String getUnreadableValue() {
    return unreadableValue;
  }
}
