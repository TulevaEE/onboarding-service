package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.RebateKind.FIXED;
import static ee.tuleva.onboarding.investment.fees.RebateKind.NONE;
import static ee.tuleva.onboarding.investment.fees.RebateKind.SHARE_OF_PUBLISHED;
import static ee.tuleva.onboarding.investment.fees.RebateKind.TIERED_VOLUME;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RebateCalculatorTest {

  private static final BigDecimal PUBLISHED_OCF = new BigDecimal("0.00070000");
  private static final BigDecimal EUR_USD = new BigDecimal("1.10000000");

  private final RebateCalculator calculator = new RebateCalculator();

  @Test
  void noAgreementRebatesNothing() {
    var rebate = calculator.rebateRate(NONE, Map.of(), inputs(null));

    assertThat(rebate).contains(new BigDecimal("0E-8"));
  }

  @Test
  void fixedRebateIsTheStoredRate() {
    var rebate = calculator.rebateRate(FIXED, Map.of("rate", "0.00020000"), inputs(null));

    assertThat(rebate).contains(new BigDecimal("0.00020000"));
  }

  @Test
  void shareRebateIsAProportionOfThePublishedOcf() {
    var rebate = calculator.rebateRate(SHARE_OF_PUBLISHED, Map.of("share", "0.30"), inputs(null));

    assertThat(rebate).contains(new BigDecimal("0.00021000"));
  }

  @Test
  void tieredRebateBlendsBothRatesAroundTheThreshold() {
    // 110m USD at 1.10 is a 100m EUR threshold, so 200m EUR sits half below and half above.
    var rebate = calculator.rebateRate(TIERED_VOLUME, usdTiers(), inputs("200000000.00"));

    assertThat(rebate).contains(new BigDecimal("0.00030000"));
  }

  @Test
  void tieredRebateBelowTheThresholdIsEntirelyTheBelowRate() {
    var rebate = calculator.rebateRate(TIERED_VOLUME, usdTiers(), inputs("50000000.00"));

    assertThat(rebate).contains(new BigDecimal("0.00040000"));
  }

  @Test
  void tieredThresholdCanBeDenominatedInEurWithoutAnExchangeRate() {
    var terms =
        Map.<String, Object>of(
            "threshold", "100000000.00",
            "currency", "EUR",
            "rateBelow", "0.00040000",
            "rateAbove", "0.00020000");

    var rebate = calculator.rebateRate(TIERED_VOLUME, terms, noFxInputs("200000000.00"));

    assertThat(rebate).contains(new BigDecimal("0.00030000"));
  }

  @Test
  void volumeGateSuppressesTheRebateEntirelyBelowTheMinimum() {
    var terms = Map.<String, Object>of("rate", "0.00020000", "minimum", "50000000.00");

    var rebate = calculator.rebateRate(FIXED, terms, inputs("49999999.99"));

    assertThat(rebate).contains(new BigDecimal("0E-8"));
  }

  @Test
  void volumeGateLetsTheRebateThroughAtTheMinimum() {
    var terms = Map.<String, Object>of("rate", "0.00020000", "minimum", "50000000.00");

    var rebate = calculator.rebateRate(FIXED, terms, inputs("50000000.00"));

    assertThat(rebate).contains(new BigDecimal("0.00020000"));
  }

  @Test
  void volumeGateInUsdIsConvertedBeforeComparing() {
    // 55m USD at 1.10 is a 50m EUR minimum, so 49m EUR stays below it.
    var terms =
        Map.<String, Object>of(
            "rate", "0.00020000", "minimum", "55000000.00", "minimumCurrency", "USD");

    var rebate = calculator.rebateRate(FIXED, terms, inputs("49000000.00"));

    assertThat(rebate).contains(new BigDecimal("0E-8"));
  }

  @Test
  void tieredRebateWithoutAVolumeCannotBeComputed() {
    var rebate = calculator.rebateRate(TIERED_VOLUME, usdTiers(), inputs(null));

    assertThat(rebate).isEmpty();
  }

  @Test
  void usdThresholdWithoutAnExchangeRateCannotBeComputed() {
    var rebate = calculator.rebateRate(TIERED_VOLUME, usdTiers(), noFxInputs("200000000.00"));

    assertThat(rebate).isEmpty();
  }

  @Test
  void gatedRebateWithoutAVolumeCannotBeComputed() {
    var terms = Map.<String, Object>of("rate", "0.00020000", "minimum", "50000000.00");

    var rebate = calculator.rebateRate(FIXED, terms, inputs(null));

    assertThat(rebate).isEmpty();
  }

  private static Map<String, Object> usdTiers() {
    return Map.of(
        "threshold", "110000000.00",
        "currency", "USD",
        "rateBelow", "0.00040000",
        "rateAbove", "0.00020000");
  }

  private static RebateInputs inputs(String volumeEur) {
    return new RebateInputs(
        PUBLISHED_OCF, volumeEur == null ? null : new BigDecimal(volumeEur), EUR_USD);
  }

  private static RebateInputs noFxInputs(String volumeEur) {
    return new RebateInputs(PUBLISHED_OCF, new BigDecimal(volumeEur), null);
  }
}
