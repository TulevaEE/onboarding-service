package ee.tuleva.onboarding.investment.calculation;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TulevaFund {
  TUK75("TUK75", 2),
  TUK00("TUK00", 2),
  TUV100("TUV100", 3);

  private final String code;
  private final int pillar;

  public static List<TulevaFund> getPillar2Funds() {
    return Arrays.stream(values()).filter(fund -> fund.pillar == 2).toList();
  }

  public static List<TulevaFund> getPillar3Funds() {
    return Arrays.stream(values()).filter(fund -> fund.pillar == 3).toList();
  }

  public static TulevaFund fromCode(String code) {
    return Arrays.stream(values())
        .filter(fund -> fund.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown fund code: " + code));
  }
}
