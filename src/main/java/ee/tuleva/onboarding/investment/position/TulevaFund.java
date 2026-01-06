package ee.tuleva.onboarding.investment.position;

import java.util.Arrays;
import java.util.List;

public enum TulevaFund {
  TUK75(List.of("Tuleva Maailma Aktsiate Pensionifond")),
  TUK00(
      List.of(
          "Tuleva Maailma Volakirjade Pensionifond", "Tuleva Maailma Võlakirjade Pensionifond")),
  TUV100(List.of("Tuleva Vabatahtlik Pensionifond", "Tuleva Vabatahtlik Pensionifon"));

  private final List<String> portfolioNames;

  TulevaFund(List<String> portfolioNames) {
    this.portfolioNames = portfolioNames;
  }

  public static String normalize(String portfolioName) {
    String trimmed = portfolioName.trim();
    return Arrays.stream(values())
        .filter(
            fund -> fund.portfolioNames.stream().anyMatch(name -> name.equalsIgnoreCase(trimmed)))
        .map(TulevaFund::name)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException("Unknown portfolio: portfolioName=" + portfolioName));
  }
}
