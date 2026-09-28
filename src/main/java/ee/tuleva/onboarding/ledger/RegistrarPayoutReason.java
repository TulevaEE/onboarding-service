package ee.tuleva.onboarding.ledger;

import static java.util.stream.Collectors.toMap;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public enum RegistrarPayoutReason {
  FUND_PENSION("Fondipensioni maksete lunastamine"),
  ONE_OFF_WITHDRAWAL("Ühekordsete maksete osakute lunastamine"),
  INHERITANCE("Pensionifondi pärimisel osakute lunastamine"),
  FUND_SWITCH("Vahetamise osakute lunastamine"),
  SWITCH_TO_PENSION_INVESTMENT_ACCOUNT("Vahetamine PIK-i"),
  SECOND_PILLAR_EXIT("RAVA osakute lunastamine"),
  UNRECOGNISED();

  private static final Pattern INVISIBLE_CHARACTERS =
      Pattern.compile("[\\u00AD\\u200B-\\u200F\\u2060-\\u2064\\uFEFF]");
  private static final Pattern WHITESPACE_RUNS = Pattern.compile("[\\s\\p{Z}]+");

  private static final Map<String, RegistrarPayoutReason> BY_NORMALISED_REMITTANCE =
      Arrays.stream(values())
          .flatMap(
              reason ->
                  Stream.of(reason.remittances)
                      .map(remittance -> Map.entry(normalise(remittance), reason)))
          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

  private final String[] remittances;

  RegistrarPayoutReason(String... remittances) {
    this.remittances = remittances;
  }

  public static RegistrarPayoutReason fromRemittance(@Nullable String remittance) {
    if (remittance == null) {
      return UNRECOGNISED;
    }
    return BY_NORMALISED_REMITTANCE.getOrDefault(normalise(remittance), UNRECOGNISED);
  }

  private static String normalise(String remittance) {
    var visible = INVISIBLE_CHARACTERS.matcher(remittance).replaceAll("");
    var composed = Normalizer.normalize(visible, Normalizer.Form.NFC);
    return WHITESPACE_RUNS.matcher(composed).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
  }
}
