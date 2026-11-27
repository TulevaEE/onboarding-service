package ee.tuleva.onboarding.savings.fund.nav;

import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SavingsFundPublicNav {

  private static final String NAV_PROVIDER = "TULEVA";
  private static final int INDEX_VALUES_DECIMAL_PLACES = 5;

  private final FundValueWriter fundValueWriter;
  private final FundValueQueries fundValueQueries;

  private record PublicValues(FundValue nav, FundValue aum) {
    List<FundValue> all() {
      return List.of(nav, aum);
    }
  }

  void publish(NavCalculationResult result) {
    publicValuesOf(result).ifPresent(values -> values.all().forEach(fundValueWriter::save));
  }

  Optional<String> differenceFrom(NavCalculationResult result) {
    return publicValuesOf(result).flatMap(this::differenceFromIndexValues);
  }

  private Optional<PublicValues> publicValuesOf(NavCalculationResult result) {
    if (!result.fund().isSavingsFund()) {
      return Optional.empty();
    }
    return Optional.of(
        new PublicValues(
            publicValue(result.fund().getIsin(), result.navPerUnit(), result),
            publicValue(result.fund().getAumKey(), result.aum(), result)));
  }

  private Optional<String> differenceFromIndexValues(PublicValues recalculated) {
    Optional<BigDecimal> publicNav = storedValueOf(recalculated.nav());
    Optional<BigDecimal> publicAum = storedValueOf(recalculated.aum());
    if (!differs(publicNav, recalculated.nav()) && !differs(publicAum, recalculated.aum())) {
      return Optional.empty();
    }
    return Optional.of(
        "The public NAV in index_values differs from the recalculation: publicNav=%s, recalculatedNav=%s, publicAum=%s, recalculatedAum=%s"
            .formatted(
                plain(publicNav),
                recalculated.nav().value().toPlainString(),
                plain(publicAum),
                recalculated.aum().value().toPlainString()));
  }

  private Optional<BigDecimal> storedValueOf(FundValue recalculated) {
    return fundValueQueries
        .getValueForDate(recalculated.key(), recalculated.date())
        .map(FundValue::value);
  }

  private static FundValue publicValue(String key, BigDecimal value, NavCalculationResult result) {
    return new FundValue(
        key, result.positionReportDate(), value, NAV_PROVIDER, result.calculatedAt());
  }

  private static boolean differs(Optional<BigDecimal> publicValue, FundValue recalculated) {
    BigDecimal asIndexValuesStoresIt =
        recalculated.value().setScale(INDEX_VALUES_DECIMAL_PLACES, HALF_UP);
    return publicValue.filter(value -> value.compareTo(asIndexValuesStoresIt) != 0).isPresent();
  }

  private static String plain(Optional<BigDecimal> value) {
    return value.map(BigDecimal::toPlainString).orElse("not stored");
  }
}
