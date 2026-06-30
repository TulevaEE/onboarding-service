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
      "taiendav-kogumisfond",
      false);

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

  public String buildPdfFilename(java.time.YearMonth month) {
    return titleGenitive
        + " investeeringute aruanne "
        + month.getYear()
        + "-"
        + String.format("%02d", month.getMonthValue())
        + ".pdf";
  }
}
