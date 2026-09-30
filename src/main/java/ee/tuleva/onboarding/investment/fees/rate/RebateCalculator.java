package ee.tuleva.onboarding.investment.fees.rate;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Computed;
import ee.tuleva.onboarding.investment.fees.rate.AgreementOutcome.Uncomputable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
class RebateCalculator {

  private static final int RATE_SCALE = 8;
  private static final BigDecimal NOTHING = ZERO.setScale(RATE_SCALE);
  private static final String EUR = "EUR";
  private static final String USD = "USD";

  AgreementOutcome apply(RebateKind kind, Map<String, Object> terms, RebateInputs inputs) {
    if (kind == RebateKind.NONE) {
      return rebate(NOTHING);
    }
    var missing = requiredTerms(kind, terms).stream().filter(not(terms::containsKey)).toList();
    if (!missing.isEmpty()) {
      return new Uncomputable("the " + kind + " agreement is missing terms=" + missing);
    }
    if (terms.containsKey("minimum")) {
      var belowTheGate = belowTheVolumeGate(terms, inputs);
      if (belowTheGate.isEmpty()) {
        return new Uncomputable(
            "the volume gate needs the month's volume and, for a USD minimum, the EUR/USD rate");
      }
      if (belowTheGate.get()) {
        return rebate(NOTHING);
      }
    }
    return withinZeroAndThePublishedOcf(
        switch (kind) {
          case NONE -> rebate(NOTHING);
          case FIXED -> rebate(decimal(terms, "rate"));
          case SHARE_OF_PUBLISHED ->
              rebate(inputs.publishedOcf().multiply(decimal(terms, "share")));
          case FIXED_NET -> fixedNet(decimal(terms, "net"), inputs.publishedOcf());
          case TIERED_VOLUME -> tiered(terms, inputs);
        },
        inputs.publishedOcf());
  }

  private static List<String> requiredTerms(RebateKind kind, Map<String, Object> terms) {
    var ofTheKind =
        switch (kind) {
          case NONE -> List.<String>of();
          case FIXED -> List.of("rate");
          case SHARE_OF_PUBLISHED -> List.of("share");
          case FIXED_NET -> List.of("net");
          case TIERED_VOLUME -> List.of("threshold", "currency", "rateBelow", "rateAbove", "funds");
        };
    var ofTheGate =
        terms.containsKey("minimum") ? List.of("minimumCurrency", "funds") : List.<String>of();
    return Stream.concat(ofTheKind.stream(), ofTheGate.stream()).distinct().toList();
  }

  private static AgreementOutcome withinZeroAndThePublishedOcf(
      AgreementOutcome outcome, BigDecimal publishedOcf) {
    if (outcome instanceof Computed computed
        && (computed.rebateRate().signum() < 0
            || computed.rebateRate().compareTo(publishedOcf) > 0)) {
      return new Uncomputable(
          "the agreement gives a rebate outside zero and the published OCF: rebate=%s, publishedOcf=%s"
              .formatted(computed.rebateRate().toPlainString(), publishedOcf.toPlainString()));
    }
    return outcome;
  }

  private static Optional<Boolean> belowTheVolumeGate(
      Map<String, Object> terms, RebateInputs inputs) {
    var volume = inputs.volumeEur();
    if (volume == null) {
      return Optional.empty();
    }
    return inEur(decimal(terms, "minimum"), currency(terms, "minimumCurrency"), inputs)
        .map(minimum -> volume.compareTo(minimum) < 0);
  }

  private static AgreementOutcome fixedNet(BigDecimal net, BigDecimal publishedOcf) {
    return computed(publishedOcf.subtract(net).max(ZERO), net.subtract(publishedOcf).max(ZERO));
  }

  private static AgreementOutcome tiered(Map<String, Object> terms, RebateInputs inputs) {
    var volume = inputs.volumeEur();
    if (volume == null) {
      return new Uncomputable("the tiered agreement needs the month's volume");
    }
    var threshold = inEur(decimal(terms, "threshold"), currency(terms, "currency"), inputs);
    if (threshold.isEmpty()) {
      return new Uncomputable("the tiered agreement's USD threshold needs the EUR/USD rate");
    }
    var rateBelow = decimal(terms, "rateBelow");
    if (volume.signum() == 0) {
      return rebate(rateBelow);
    }
    var belowThreshold = volume.min(threshold.get());
    var aboveThreshold = volume.subtract(threshold.get()).max(ZERO);
    var blended =
        belowThreshold
            .multiply(rateBelow)
            .add(aboveThreshold.multiply(decimal(terms, "rateAbove")))
            .divide(volume, MathContext.DECIMAL64);
    return rebate(blended);
  }

  private static Optional<BigDecimal> inEur(
      BigDecimal amount, String currency, RebateInputs inputs) {
    return switch (currency) {
      case EUR -> Optional.of(amount);
      case USD ->
          Optional.ofNullable(inputs.eurUsd())
              .map(eurUsd -> amount.divide(eurUsd, MathContext.DECIMAL64));
      default ->
          throw new IllegalArgumentException(
              "Unsupported agreement currency: currency=" + currency);
    };
  }

  private static String currency(Map<String, Object> terms, String key) {
    return String.valueOf(terms.get(key));
  }

  private static BigDecimal decimal(Map<String, Object> terms, String key) {
    return new BigDecimal(String.valueOf(terms.get(key)));
  }

  private static AgreementOutcome rebate(BigDecimal rebateRate) {
    return computed(rebateRate, NOTHING);
  }

  private static AgreementOutcome computed(BigDecimal rebateRate, BigDecimal invoicedFeeRate) {
    return new Computed(
        rebateRate.setScale(RATE_SCALE, HALF_UP), invoicedFeeRate.setScale(RATE_SCALE, HALF_UP));
  }
}
