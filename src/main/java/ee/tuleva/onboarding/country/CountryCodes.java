package ee.tuleva.onboarding.country;

import static java.util.Locale.IsoCountryCode.PART1_ALPHA2;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

public final class CountryCodes {

  private static final Map<String, String> ALPHA3_TO_ALPHA2 =
      Locale.getISOCountries(PART1_ALPHA2).stream()
          .filter(alpha2 -> iso3Of(alpha2) != null)
          .collect(toUnmodifiableMap(CountryCodes::iso3Of, identity()));

  // ISO 3166-1 numeric, the classifier the Estonian population register returns as
  // citizenship.riik.elemendiKood. Covers every EEA and AML high-risk country the risk views
  // score on, plus common others; CountryCodesSpec fails if one of those goes missing.
  private static final String NUMERIC_PAIRS =
      """
      004=AF,012=DZ,024=AO,036=AU,040=AT,056=BE,070=BA,100=BG,104=MM,108=BI,
      112=BY,120=CM,124=CA,140=CF,156=CN,180=CD,191=HR,192=CU,196=CY,203=CZ,
      208=DK,233=EE,246=FI,250=FR,275=PS,276=DE,300=GR,320=GT,324=GN,332=HT,
      348=HU,352=IS,356=IN,364=IR,368=IQ,372=IE,380=IT,392=JP,400=JO,408=KP,
      417=KG,422=LB,428=LV,434=LY,438=LI,440=LT,442=LU,466=ML,470=MT,499=ME,
      504=MA,508=MZ,528=NL,558=NI,562=NE,566=NG,578=NO,586=PK,616=PL,620=PT,
      624=GW,642=RO,643=RU,688=RS,703=SK,705=SI,706=SO,716=ZW,724=ES,728=SS,
      729=SD,752=SE,756=CH,760=SY,762=TJ,784=AE,788=TN,792=TR,795=TM,804=UA,
      818=EG,826=GB,840=US,854=BF,862=VE,887=YE
      """;

  private static final Map<Integer, String> NUMERIC_TO_ALPHA2 =
      NUMERIC_PAIRS
          .lines()
          .flatMap(line -> Arrays.stream(line.split(",")))
          .map(String::strip)
          .filter(pair -> !pair.isEmpty())
          .map(pair -> pair.split("="))
          .collect(toUnmodifiableMap(pair -> Integer.valueOf(pair[0]), pair -> pair[1]));

  private CountryCodes() {}

  public static @Nullable String toAlpha2(@Nullable String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    var alpha3 = code.strip().toUpperCase(Locale.ROOT);
    return ALPHA3_TO_ALPHA2.getOrDefault(alpha3, code);
  }

  public static @Nullable String numericToAlpha2(@Nullable String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    var trimmed = code.strip();
    if (!trimmed.chars().allMatch(Character::isDigit)) {
      return trimmed;
    }
    return NUMERIC_TO_ALPHA2.getOrDefault(Integer.valueOf(trimmed), trimmed);
  }

  public static Set<String> mappedAlpha2Codes() {
    return Set.copyOf(NUMERIC_TO_ALPHA2.values());
  }

  private static @Nullable String iso3Of(String alpha2) {
    try {
      return new Locale.Builder().setRegion(alpha2).build().getISO3Country();
    } catch (MissingResourceException e) {
      return null;
    }
  }
}
