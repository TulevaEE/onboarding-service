package ee.tuleva.onboarding.investment.report.publishing;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.net.URLDecoder;
import java.time.YearMonth;
import java.util.List;

sealed interface ReportPublication {

  TulevaFund fund();

  String describe();

  default boolean isPublished() {
    return switch (this) {
      case Published _ -> true;
      case Outdated _, ForeignFile _, NotLinked _, NotChecked _ -> false;
    };
  }

  default boolean isMissing() {
    return switch (this) {
      case Outdated _, ForeignFile _, NotLinked _ -> true;
      case Published _, NotChecked _ -> false;
    };
  }

  static boolean allPublished(List<ReportPublication> publications) {
    return publications.stream().allMatch(ReportPublication::isPublished);
  }

  static ReportPublication linking(
      TulevaFund fund, String reportUrl, YearMonth month, YearMonth lastMonth) {
    return FundReportMapping.forFund(fund)
        .reportPeriodNamedBy(filename(reportUrl))
        .filter(period -> !period.isAfter(lastMonth))
        .<ReportPublication>map(
            period ->
                period.isBefore(month)
                    ? new Outdated(fund, reportUrl, month)
                    : new Published(fund, reportUrl))
        .orElseGet(() -> new ForeignFile(fund, reportUrl));
  }

  private static String filename(String url) {
    return URLDecoder.decode(url.substring(url.lastIndexOf('/') + 1), UTF_8);
  }

  record Published(TulevaFund fund, String reportUrl) implements ReportPublication {

    @Override
    public String describe() {
      return "%s: %s".formatted(fund.getCode(), filename(reportUrl));
    }
  }

  record Outdated(TulevaFund fund, String reportUrl, YearMonth required)
      implements ReportPublication {

    @Override
    public String describe() {
      return "%s: the fund page links %s, not the %s report"
          .formatted(fund.getCode(), filename(reportUrl), required);
    }
  }

  record ForeignFile(TulevaFund fund, String reportUrl) implements ReportPublication {

    @Override
    public String describe() {
      return "%s: the fund page links %s, which is not this fund's investment report"
          .formatted(fund.getCode(), filename(reportUrl));
    }
  }

  record NotLinked(TulevaFund fund) implements ReportPublication {

    @Override
    public String describe() {
      return "%s: the fund page links no investment report".formatted(fund.getCode());
    }
  }

  record NotChecked(TulevaFund fund, String reason) implements ReportPublication {

    @Override
    public String describe() {
      return "%s: could not check — %s".formatted(fund.getCode(), reason);
    }
  }
}
