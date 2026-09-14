package ee.tuleva.onboarding.investment.report;

public class MissingReportAsOfDateException extends RuntimeException {

  public MissingReportAsOfDateException(ReportProvider provider, ReportType reportType) {
    super("No 'As of' date in report: provider=%s, reportType=%s".formatted(provider, reportType));
  }
}
