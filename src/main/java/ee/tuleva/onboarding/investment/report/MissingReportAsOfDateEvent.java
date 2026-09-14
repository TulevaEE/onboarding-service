package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record MissingReportAsOfDateEvent(
    ReportProvider provider,
    ReportType reportType,
    LocalDate reportDate,
    @Nullable String unreadableValue) {

  public MissingReportAsOfDateEvent(
      ReportProvider provider, ReportType reportType, LocalDate reportDate) {
    this(provider, reportType, reportDate, null);
  }
}
