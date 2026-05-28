package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class SebClientNameToFundResolver {

  // Handles diacritic variants only (e.g. SEB strips õ/ä in TULEVA_PORTFOLIO export).
  // Does NOT handle name aliases such as "Tuleva Vabatahtlik Pensionifon(d)" vs "Tuleva III Samba
  // Pensionifond" — if such ledger-name divergences appear in the daily feed, handle separately.
  private static final Map<String, TulevaFund> BY_NORMALIZED_DISPLAY_NAME =
      Arrays.stream(TulevaFund.values())
          .collect(
              Collectors.toUnmodifiableMap(
                  fund -> normalize(fund.getDisplayName()), Function.identity()));

  Optional<TulevaFund> resolve(String clientName) {
    if (clientName == null) {
      return Optional.empty();
    }
    String normalized = normalize(clientName);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_NORMALIZED_DISPLAY_NAME.get(normalized));
  }

  private static String normalize(String s) {
    if (s == null) return "";
    String decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
    return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT);
  }
}
