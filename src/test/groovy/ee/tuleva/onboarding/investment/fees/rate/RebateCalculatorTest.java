package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED_NET;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.NONE;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.SHARE_OF_PUBLISHED;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.TIERED_VOLUME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Uncomputable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RebateCalculatorTest {

  private static final BigDecimal PUBLISHED_OCF = new BigDecimal("0.00070000");
  private static final BigDecimal EUR_USD = new BigDecimal("1.10000000");

  private final RebateCalculator calculator = new RebateCalculator();

  @Test
  void noAgreementRebatesNothing() {
    assertThat(calculator.apply(NONE, Map.of(), inputs(null))).isEqualTo(rebate("0.00000000"));
  }

  @Test
  void fixedRebateIsTheStoredRate() {
    assertThat(calculator.apply(FIXED, Map.of("rate", "0.00020000"), inputs(null)))
        .isEqualTo(rebate("0.00020000"));
  }

  @Test
  void shareRebateIsAProportionOfThePublishedOcf() {
    assertThat(calculator.apply(SHARE_OF_PUBLISHED, Map.of("share", "0.30"), inputs(null)))
        .isEqualTo(rebate("0.00021000"));
  }

  @Test
  void tieredRebateBlendsBothRatesAroundTheThreshold() {
    assertThat(calculator.apply(TIERED_VOLUME, usdTiers(), inputs("200000000.00")))
        .isEqualTo(rebate("0.00030000"));
  }

  @Test
  void tieredRebateBelowTheThresholdIsEntirelyTheBelowRate() {
    assertThat(calculator.apply(TIERED_VOLUME, usdTiers(), inputs("50000000.00")))
        .isEqualTo(rebate("0.00040000"));
  }

  @Test
  void tieredThresholdCanBeDenominatedInEurWithoutAnExchangeRate() {
    var terms =
        Map.<String, Object>of(
            "threshold", "100000000.00",
            "currency", "EUR",
            "rateBelow", "0.00040000",
            "rateAbove", "0.00020000",
            "funds", List.of("TUK75"));

    assertThat(calculator.apply(TIERED_VOLUME, terms, noFxInputs("200000000.00")))
        .isEqualTo(rebate("0.00030000"));
  }

  @Test
  void aFixedNetBelowThePublishedOcfIsARebateThatFloatsWithThePublishedOcf() {
    var terms = Map.<String, Object>of("net", "0.00050000");

    assertThat(calculator.apply(FIXED_NET, terms, inputs(null))).isEqualTo(rebate("0.00020000"));
    assertThat(calculator.apply(FIXED_NET, terms, publishedAt("0.00080000")))
        .isEqualTo(rebate("0.00030000"));
  }

  @Test
  void aFixedNetAboveThePublishedOcfIsBorneByTheFundAsAnInvoicedFee() {
    var terms = Map.<String, Object>of("net", "0.00090000");

    assertThat(calculator.apply(FIXED_NET, terms, inputs(null)))
        .isEqualTo(new Computed(new BigDecimal("0.00000000"), new BigDecimal("0.00020000")));
  }

  @Test
  void aGatedFixedNetBelowTheMinimumLeavesThePublishedOcf() {
    var terms =
        Map.<String, Object>of(
            "net",
            "0.00050000",
            "minimum",
            "50000000.00",
            "minimumCurrency",
            "EUR",
            "funds",
            List.of("TUK75"));

    assertThat(calculator.apply(FIXED_NET, terms, inputs("49999999.99")))
        .isEqualTo(rebate("0.00000000"));
    assertThat(calculator.apply(FIXED_NET, terms, inputs("50000000.00")))
        .isEqualTo(rebate("0.00020000"));
  }

  @Test
  void volumeGateSuppressesTheRebateEntirelyBelowTheMinimum() {
    var terms = eurGate("0.00020000", "50000000.00");

    assertThat(calculator.apply(FIXED, terms, inputs("49999999.99")))
        .isEqualTo(rebate("0.00000000"));
  }

  @Test
  void volumeGateLetsTheRebateThroughAtTheMinimum() {
    var terms = eurGate("0.00020000", "50000000.00");

    assertThat(calculator.apply(FIXED, terms, inputs("50000000.00")))
        .isEqualTo(rebate("0.00020000"));
  }

  @Test
  void volumeGateInUsdIsConvertedBeforeComparing() {
    var terms = usdGate("0.00020000", "55000000.00");

    assertThat(calculator.apply(FIXED, terms, inputs("49000000.00")))
        .isEqualTo(rebate("0.00000000"));
  }

  @Test
  void volumeGateInUsdIsDividedByTheEurUsdRateSoAVolumeBetweenTheTwoReadingsPasses() {
    var fiftyFiveMillionUsdIsFiftyMillionEurAtOneTen = usdGate("0.00020000", "55000000.00");

    assertThat(
            calculator.apply(
                FIXED, fiftyFiveMillionUsdIsFiftyMillionEurAtOneTen, inputs("52000000.00")))
        .isEqualTo(rebate("0.00020000"));
  }

  @Test
  void aVolumeGateWithoutItsCurrencyOrFundsCannotBeComputedRatherThanGuessed() {
    var terms = Map.<String, Object>of("rate", "0.00020000", "minimum", "50000000.00");

    assertThat(calculator.apply(FIXED, terms, inputs("60000000.00")))
        .isEqualTo(
            new Uncomputable("the FIXED agreement is missing terms=[minimumCurrency, funds]"));
  }

  @Test
  void aTieredAgreementWithoutItsCurrencyOrFundsCannotBeComputedRatherThanGuessed() {
    var terms =
        Map.<String, Object>of(
            "threshold", "110000000.00", "rateBelow", "0.00040000", "rateAbove", "0.00020000");

    assertThat(calculator.apply(TIERED_VOLUME, terms, inputs("200000000.00")))
        .isEqualTo(
            new Uncomputable("the TIERED_VOLUME agreement is missing terms=[currency, funds]"));
  }

  @Test
  void aNegativeRebateIsRefusedRatherThanStoredAsACharge() {
    assertThat(calculator.apply(FIXED, Map.of("rate", "-0.00020000"), inputs(null)))
        .isEqualTo(
            new Uncomputable(
                "the agreement gives a rebate outside zero and the published OCF:"
                    + " rebate=-0.00020000, publishedOcf=0.00070000"));
  }

  @Test
  void aRebateAboveThePublishedOcfIsRefusedRatherThanStoredAsANegativeCost() {
    assertThat(calculator.apply(SHARE_OF_PUBLISHED, Map.of("share", "1.5"), inputs(null)))
        .isEqualTo(
            new Uncomputable(
                "the agreement gives a rebate outside zero and the published OCF:"
                    + " rebate=0.00105000, publishedOcf=0.00070000"));
  }

  @Test
  void tieredRebateWithoutAVolumeCannotBeComputed() {
    assertThat(calculator.apply(TIERED_VOLUME, usdTiers(), inputs(null)))
        .isInstanceOf(Uncomputable.class);
  }

  @Test
  void usdThresholdWithoutAnExchangeRateCannotBeComputed() {
    assertThat(calculator.apply(TIERED_VOLUME, usdTiers(), noFxInputs("200000000.00")))
        .isInstanceOf(Uncomputable.class);
  }

  @Test
  void gatedRebateWithoutAVolumeCannotBeComputed() {
    var terms = eurGate("0.00020000", "50000000.00");

    assertThat(calculator.apply(FIXED, terms, inputs(null))).isInstanceOf(Uncomputable.class);
  }

  @Test
  void anAgreementMissingATermItsKindNeedsCannotBeComputed() {
    assertThat(calculator.apply(FIXED_NET, Map.of("rate", "0.00020000"), inputs(null)))
        .isInstanceOf(Uncomputable.class);
  }

  @Test
  void aGateInACurrencyOtherThanEurOrUsdIsRefusedRatherThanReadAsEur() {
    var terms =
        Map.<String, Object>of(
            "rate",
            "0.00020000",
            "minimum",
            "50000000.00",
            "minimumCurrency",
            "GBP",
            "funds",
            List.of("TUK75"));

    assertThatThrownBy(() -> calculator.apply(FIXED, terms, inputs("60000000.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void termsStoredAsJsonNumbersReadTheSameAsStrings() {
    assertThat(calculator.apply(FIXED, Map.of("rate", 0.0002), inputs(null)))
        .isEqualTo(rebate("0.00020000"));
  }

  private static Map<String, Object> usdTiers() {
    return Map.of(
        "threshold", "110000000.00",
        "currency", "USD",
        "rateBelow", "0.00040000",
        "rateAbove", "0.00020000",
        "funds", List.of("TUK75"));
  }

  private static Map<String, Object> eurGate(String rate, String minimum) {
    return Map.of(
        "rate", rate, "minimum", minimum, "minimumCurrency", "EUR", "funds", List.of("TUK75"));
  }

  private static Map<String, Object> usdGate(String rate, String minimum) {
    return Map.of(
        "rate", rate, "minimum", minimum, "minimumCurrency", "USD", "funds", List.of("TUK75"));
  }

  private static Computed rebate(String rate) {
    return new Computed(new BigDecimal(rate), new BigDecimal("0.00000000"));
  }

  private static RebateInputs inputs(@Nullable String volumeEur) {
    return new RebateInputs(
        PUBLISHED_OCF, volumeEur == null ? null : new BigDecimal(volumeEur), EUR_USD);
  }

  private static RebateInputs publishedAt(String publishedOcf) {
    return new RebateInputs(new BigDecimal(publishedOcf), null, EUR_USD);
  }

  private static RebateInputs noFxInputs(String volumeEur) {
    return new RebateInputs(PUBLISHED_OCF, new BigDecimal(volumeEur), null);
  }
}
