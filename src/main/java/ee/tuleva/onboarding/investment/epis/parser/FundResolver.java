package ee.tuleva.onboarding.investment.epis.parser;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class FundResolver {

  private FundResolver() {}

  static Optional<TulevaFund> resolve(@Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String trimmed = raw.trim();
    return TulevaFund.findByIsin(trimmed.toUpperCase(Locale.ROOT))
        .or(() -> TulevaFund.findByDisplayName(trimmed))
        .or(() -> containsMatch(trimmed));
  }

  private static Optional<TulevaFund> containsMatch(String raw) {
    String lowerCase = raw.toLowerCase(Locale.ROOT);
    return Arrays.stream(TulevaFund.values())
        .filter(
            fund ->
                lowerCase.contains(fund.getDisplayName().toLowerCase(Locale.ROOT))
                    || lowerCase.contains(fund.getCode().toLowerCase(Locale.ROOT))
                    || lowerCase.contains(fund.getIsin().toLowerCase(Locale.ROOT)))
        .findFirst();
  }
}
