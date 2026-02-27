package ee.tuleva.onboarding.fund;

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
      false,
      5),
  TUK00(
      "TUK00",
      2,
      "EE3600109443",
      "Tuleva Maailma V\u00f5lakirjade Pensionifond",
      "VP68170",
      "EE021010220306593225",
      false,
      5),
  TUV100(
      "TUV100",
      3,
      "EE3600001707",
      "Tuleva III Samba Pensionifond",
      "VP68959",
      "EE691010220306737229",
      false,
      5),
  TKF100(
      "TKF100",
      null,
      "EE0000003283",
      "Tuleva T\u00e4iendav Kogumisfond",
      "VP68168",
      "EE861010220306591229",
      true,
      4);

  private final String code;
  private final @Nullable Integer pillar;
  private final String isin;
  private final String displayName;
  private final String securitiesAccount;
  private final String cashAccount;
  private final boolean navCalculation;
  private final int navScale;

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
