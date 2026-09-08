package ee.tuleva.onboarding.investment.report;

// The "As of" preamble marker dates every row in a SEB report. Without it the rows cannot be placed
// in time, and the file's own date is the day it was sent — a different clock, one business day
// later. Substituting it silently shifts positions onto the wrong NAV date and stamps executions as
// unreported when the custodian has already reported them, so the report is refused instead.
public class MissingReportAsOfDateException extends RuntimeException {

  public MissingReportAsOfDateException(ReportProvider provider, ReportType reportType) {
    super("No 'As of' date in report: provider=%s, reportType=%s".formatted(provider, reportType));
  }
}
