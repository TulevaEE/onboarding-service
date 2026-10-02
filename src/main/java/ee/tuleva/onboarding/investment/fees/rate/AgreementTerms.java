package ee.tuleva.onboarding.investment.fees.rate;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record AgreementTerms(Map<String, Object> values) {

  static AgreementTerms of(@Nullable Map<String, Object> parsedTerms) {
    if (parsedTerms == null) {
      throw new IllegalArgumentException("Agreement terms are JSON null");
    }
    return new AgreementTerms(parsedTerms);
  }

  boolean needTheMonthsVolume(RebateKind kind) {
    return kind == RebateKind.TIERED_VOLUME || values.containsKey("minimum");
  }

  boolean needAnExchangeRate() {
    return "USD".equals(String.valueOf(values.get("currency")))
        || "USD".equals(String.valueOf(values.get("minimumCurrency")));
  }

  List<TulevaFund> volumeFunds() {
    if (values.get("funds") instanceof Collection<?> funds) {
      return funds.stream().map(String::valueOf).map(TulevaFund::valueOf).toList();
    }
    return List.of();
  }
}
