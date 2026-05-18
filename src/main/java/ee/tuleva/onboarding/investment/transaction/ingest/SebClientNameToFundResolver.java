package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class SebClientNameToFundResolver {

  private static final Map<String, TulevaFund> BY_DISPLAY_NAME =
      Arrays.stream(TulevaFund.values())
          .collect(Collectors.toUnmodifiableMap(TulevaFund::getDisplayName, Function.identity()));

  Optional<TulevaFund> resolve(String clientName) {
    if (clientName == null) {
      return Optional.empty();
    }
    String trimmed = clientName.trim();
    if (trimmed.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_DISPLAY_NAME.get(trimmed));
  }
}
