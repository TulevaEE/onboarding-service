package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Pattern;

sealed interface ReportPublication {

  Pattern PERIOD_IN_FILENAME = Pattern.compile("(?<!\\d)(\\d{4})-(0[1-9]|1[0-2])(?!\\d)");
  Pattern UPLOAD_FOLDER = Pattern.compile("/(\\d{4})/(0[1-9]|1[0-2])/");

  TulevaFund fund();

  String line();

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

  static ReportPublication linking(TulevaFund fund, String reportUrl, YearMonth month) {
    return periodTheFundPageShows(reportUrl).filter(period -> !period.isBefore(month)).isPresent()
        ? new Published(fund, reportUrl)
        : new Outdated(fund, reportUrl);
  }

  private static Optional<YearMonth> periodTheFundPageShows(String reportUrl) {
    return periodIn(PERIOD_IN_FILENAME, filename(reportUrl))
        .or(() -> periodIn(UPLOAD_FOLDER, reportUrl).map(folder -> folder.minusMonths(1)));
  }

  private static Optional<YearMonth> periodIn(Pattern pattern, String text) {
    var matcher = pattern.matcher(text);
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
    public String line() {
      return "✅ %s: %s".formatted(fund.getCode(), filename(reportUrl));
    }
  }

  record Outdated(TulevaFund fund, String reportUrl) implements ReportPublication {

    @Override
    public String line() {
      return "🔴 %s: the fund page still links %s".formatted(fund.getCode(), filename(reportUrl));
    }
  }

  record NotLinked(TulevaFund fund) implements ReportPublication {

    @Override
    public String line() {
      return "🔴 %s: the fund page links no investment report".formatted(fund.getCode());
    }
  }

  record NotChecked(TulevaFund fund, String reason) implements ReportPublication {

    @Override
    public String line() {
      return "⏸ %s: could not check — %s".formatted(fund.getCode(), reason);
    }
  }
}
