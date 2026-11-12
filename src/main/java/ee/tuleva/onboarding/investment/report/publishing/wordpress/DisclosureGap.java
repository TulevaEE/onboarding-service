package ee.tuleva.onboarding.investment.report.publishing.wordpress;

public record DisclosureGap(String field, Problem problem, Page page) {

  public boolean concernsTheInvestmentReport() {
    return AcfReportField.NAME.equals(field);
  }

  public enum Problem {
    MISSING,
    NO_EFFECTIVE_DATE
  }

  public enum Page {
    FUND,
    SHARED
  }
}
