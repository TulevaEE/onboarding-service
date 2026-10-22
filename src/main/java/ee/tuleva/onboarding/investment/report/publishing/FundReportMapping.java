package ee.tuleva.onboarding.investment.report.publishing;

import static java.lang.Integer.parseInt;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressSlug;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum FundReportMapping {
  TUK75(
      TulevaFund.TUK75,
      "Tuleva Maailma Aktsiate Pensionifondi",
      "Aktsiafondid",
      "tuleva-maailma-aktsiate-pensionifond",
      true),
  TUK00(
      TulevaFund.TUK00,
      "Tuleva Maailma Võlakirjade Pensionifondi",
      "Võlakirjafondid",
      "tuleva-maailma-volakirjade-pensionifond",
      true),
  TUV100(
      TulevaFund.TUV100,
      "Tuleva III Samba Pensionifondi",
      "Aktsiafondid",
      "tuleva-iii-samba-pensionifond",
      true),
  TKF100(
      TulevaFund.TKF100,
      "Tuleva Täiendava Kogumisfondi",
      "Aktsiafondid",
      "tuleva-taiendav-kogumisfond-dokumendid",
      false);

  private static final Map<TulevaFund, FundReportMapping> BY_FUND =
      Arrays.stream(values())
          .collect(Collectors.toMap(FundReportMapping::fund, Function.identity()));

  private static final String REPORT_TITLE = " investeeringute aruanne ";
  private static final Pattern REPORT_PERIOD_AT_THE_END_OF_AN_UPLOADED_NAME =
      Pattern.compile("-(\\d{4})-(0[1-9]|1[0-2])(?:-\\d+)?\\.pdf$");

  private static final String[] ESTONIAN_MONTHS = {
    "jaanuar", "veebruar", "märts", "aprill", "mai", "juuni",
    "juuli", "august", "september", "oktoober", "november", "detsember"
  };

  private final TulevaFund fund;
  private final String titleGenitive;
  private final String sectionHeading;
  private final String pageSlug;
  private final boolean includeInEmail;

  FundReportMapping(
      TulevaFund fund,
      String titleGenitive,
      String sectionHeading,
      String pageSlug,
      boolean includeInEmail) {
    this.fund = fund;
    this.titleGenitive = titleGenitive;
    this.sectionHeading = sectionHeading;
    this.pageSlug = pageSlug;
    this.includeInEmail = includeInEmail;
  }

  public TulevaFund fund() {
    return fund;
  }

  public String titleGenitive() {
    return titleGenitive;
  }

  public String sectionHeading() {
    return sectionHeading;
  }

  public String pageSlug() {
    return pageSlug;
  }

  public boolean includeInEmail() {
    return includeInEmail;
  }

  public static FundReportMapping forFund(TulevaFund fund) {
    var mapping = BY_FUND.get(fund);
    if (mapping == null) {
      throw new IllegalArgumentException("No report mapping for fund: " + fund.getCode());
    }
    return mapping;
  }

  public static List<FundReportMapping> all() {
    return List.of(values());
  }

  public static String estonianMonth(int month) {
    if (month < 1 || month > 12) {
      throw new IllegalArgumentException("Month must be between 1 and 12: " + month);
    }
    return ESTONIAN_MONTHS[month - 1];
  }

  public Optional<YearMonth> reportPeriodNamedBy(String filename) {
    var uploadedName = WordPressSlug.of(filename);
    var period = REPORT_PERIOD_AT_THE_END_OF_AN_UPLOADED_NAME.matcher(uploadedName);
    if (!period.find()) {
      return Optional.empty();
    }
    var month = YearMonth.of(parseInt(period.group(1)), parseInt(period.group(2)));
    var nameWithoutWordPressDuplicateSuffix =
        uploadedName.substring(0, period.start()) + "-%s.pdf".formatted(month);
    return nameWithoutWordPressDuplicateSuffix.equals(WordPressSlug.of(buildPdfFilename(month)))
        ? Optional.of(month)
        : Optional.empty();
  }

  public String buildPdfFilename(YearMonth month) {
    return titleGenitive
        + REPORT_TITLE
        + month.getYear()
        + "-"
        + String.format("%02d", month.getMonthValue())
        + ".pdf";
  }
}
