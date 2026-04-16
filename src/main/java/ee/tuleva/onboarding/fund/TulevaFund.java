package ee.tuleva.onboarding.fund;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public enum TulevaFund {
  TUK75(
      "TUK75",
      2,
      "EE3600109435",
      "Tuleva Maailma Aktsiate Pensionifond",
      "VP68169",
      "EE421010220306592227",
      true,
      5,
      LocalTime.parse("11:00"),
      LocalDate.parse("2017-03-28"),
      List.of(
          "IE00BFG1TM61",
          "IE0009FT4LX4",
          "IE00BFNM3G45",
          "IE00BFNM3D14",
          "IE00BFNM3L97",
          "IE00BKPTWY98")),
  TUK00(
      "TUK00",
      2,
      "EE3600109443",
      "Tuleva Maailma Võlakirjade Pensionifond",
      "VP68170",
      "EE021010220306593225",
      true,
      5,
      LocalTime.parse("11:00"),
      LocalDate.parse("2017-03-28"),
      List.of("LU0826455353", "IE0031080751", "LU0839970364", "IE0005032192")),
  TUV100(
      "TUV100",
      3,
      "EE3600001707",
      "Tuleva III Samba Pensionifond",
      "VP68959",
      "EE691010220306737229",
      true,
      4,
      LocalTime.parse("15:20"),
      LocalDate.parse("2019-10-15"),
      List.of(
          "IE00BFG1TM61",
          "IE0009FT4LX4",
          "IE00BFNM3G45",
          "IE00BFNM3D14",
          "IE00BFNM3L97",
          "IE00BKPTWY98")),
  TKF100(
      "TKF100",
      null,
      "EE0000003283",
      "Tuleva Täiendav Kogumisfond",
      "VP68168",
      "EE861010220306591229",
      true,
      4,
      LocalTime.parse("15:20"),
      LocalDate.parse("2026-02-02"),
      List.of(
          "IE00BMDBMY19",
          "IE00BFG1TM61",
          "IE00BJZ2DC62",
          "LU0476289540",
          "IE000F60HVH9",
          "IE000O58J820",
          "LU1291099718",
          "LU1291106356",
          "LU1291102447"));

  private final String code;
  private final @Nullable Integer pillar;
  private final String isin;
  private final String displayName;
  private final String securitiesAccount;
  private final String cashAccount;
  private final boolean navCalculation;
  private final int navScale;
  private final LocalTime navCutoffTime;
  private final LocalDate inceptionDate;
  private final List<String> modelPortfolioOrder;

  public static List<TulevaFund> getPillar2Funds() {
    return Arrays.stream(values()).filter(fund -> fund.pillar != null && fund.pillar == 2).toList();
  }

  public static List<TulevaFund> getPillar3Funds() {
    return Arrays.stream(values()).filter(fund -> fund.pillar != null && fund.pillar == 3).toList();
  }

  public static List<TulevaFund> getSavingsFunds() {
    return Arrays.stream(values()).filter(fund -> fund.pillar == null).toList();
  }

  public boolean hasNavCalculation() {
    return navCalculation;
  }

  public boolean isSavingsFund() {
    return pillar == null;
  }

  public String navCronExpression() {
    return "0 %d %d * * MON-FRI".formatted(navCutoffTime.getMinute(), navCutoffTime.getHour());
  }

  public String getAumKey() {
    return "AUM_" + isin;
  }

  public static TulevaFund fromCode(String code) {
    return Arrays.stream(values())
        .filter(fund -> fund.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown fund code: " + code));
  }
}
