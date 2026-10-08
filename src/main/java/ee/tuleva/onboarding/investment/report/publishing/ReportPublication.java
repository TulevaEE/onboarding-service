package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

sealed interface ReportPublication {

  Pattern PERIOD_IN_FILENAME = Pattern.compile("(?<!\\d)(\\d{4})-(0[1-9]|1[0-2])(?!\\d)");

  TulevaFund fund();

  String describe();

  default boolean isPublished() {
    return switch (this) {
      case Published _ -> true;
      case Outdated _, NotLinked _, NotChecked _ -> false;
    };
  }

  default boolean isMissing() {
    return switch (this) {
      case Outdated _, NotLinked _ -> true;
      case Published _, NotChecked _ -> false;
    };
  }

  static boolean allPublished(List<ReportPublication> publications) {
    return publications.stream().allMatch(ReportPublication::isPublished);
  }

  static ReportPublication linking(TulevaFund fund, String reportUrl, YearMonth month) {
    return periodNamedBy(filename(reportUrl)).filter(period -> !period.isBefore(month)).isPresent()
        ? new Published(fund, reportUrl)
        : new Outdated(fund, reportUrl, month);
  }

  private static Optional<YearMonth> periodNamedBy(String filename) {
    var matcher = PERIOD_IN_FILENAME.matcher(filename);
    return matcher.find()
        ? Optional.of(
            YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))))
        : Optional.empty();
  }

  private static String filename(String url) {
    return url.substring(url.lastIndexOf('/') + 1);
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
