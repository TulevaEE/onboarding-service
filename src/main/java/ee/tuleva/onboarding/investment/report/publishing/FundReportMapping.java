package ee.tuleva.onboarding.investment.report.publishing;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum FundReportMapping {
  TUK75(
      TulevaFund.TUK75,
      "Tuleva Maailma Aktsiate Pensionifondi",
      "Aktsiafondid",
      "src/wp-content/themes/tuleva/templates/components/fund-stocks-details.php",
      true,
      true),
  TUK00(
      TulevaFund.TUK00,
      "Tuleva Maailma Võlakirjade Pensionifondi",
      "Võlakirjafondid",
      "src/wp-content/themes/tuleva/templates/components/fund-bonds-details.php",
      true,
      true),
  TUV100(
      TulevaFund.TUV100,
      "Tuleva III Samba Pensionifondi",
      "Aktsiafondid",
      "src/wp-content/themes/tuleva/templates/components/fund-third-details.php",
      true,
      true),
  TKF100(TulevaFund.TKF100, "Tuleva Täiendava Kogumisfondi", "Aktsiafondid", null, false, false);

  private static final Map<TulevaFund, FundReportMapping> BY_FUND =
      Arrays.stream(values())
          .collect(Collectors.toMap(FundReportMapping::fund, Function.identity()));

  private static final String[] ESTONIAN_MONTHS = {
    "jaanuar", "veebruar", "märts", "aprill", "mai", "juuni",
    "juuli", "august", "september", "oktoober", "november", "detsember"
  };

  private final TulevaFund fund;
  private final String titleGenitive;
  private final String sectionHeading;
  private final String phpFilePath;
  private final boolean includeInPr;
  private final boolean includeInEmail;

  FundReportMapping(
      TulevaFund fund,
      String titleGenitive,
      String sectionHeading,
      String phpFilePath,
      boolean includeInPr,
      boolean includeInEmail) {
    this.fund = fund;
    this.titleGenitive = titleGenitive;
    this.sectionHeading = sectionHeading;
    this.phpFilePath = phpFilePath;
    this.includeInPr = includeInPr;
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

  public String phpFilePath() {
    return phpFilePath;
  }

  public boolean includeInPr() {
    return includeInPr;
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
    return ESTONIAN_MONTHS[month - 1];
  }

  public String buildPdfFilename(java.time.YearMonth month) {
    return titleGenitive
        + " investeeringute aruanne "
        + estonianMonth(month.getMonthValue())
        + " "
        + month.getYear()
        + ".pdf";
  }
}
