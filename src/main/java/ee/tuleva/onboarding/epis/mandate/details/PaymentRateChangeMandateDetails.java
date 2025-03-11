package ee.tuleva.onboarding.epis.mandate.details;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.PAYMENT_RATE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Arrays;
import lombok.Getter;

@Getter
public class PaymentRateChangeMandateDetails extends MandateDetails {
  @NotNull private final PaymentRate paymentRate;

  @JsonCreator
  public PaymentRateChangeMandateDetails(@JsonProperty("paymentRate") PaymentRate paymentRate) {
    super(MandateType.PAYMENT_RATE_CHANGE);
    this.paymentRate = paymentRate;
  }

  @Getter
  public enum PaymentRate {
    TWO(new BigDecimal("2")),
    FOUR(new BigDecimal("4")),
    SIX(new BigDecimal("6"));

    private final BigDecimal numericValue;

    PaymentRate(BigDecimal numericValue) {
      this.numericValue = numericValue;
    }

    public static PaymentRate fromValue(BigDecimal value) {
      return Arrays.stream(values())
          .filter(rate -> rate.numericValue.compareTo(value) == 0)
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "No corresponding discrete payment rate found for " + value));
    }
  }

  @Override
  public ApplicationType getApplicationType() {
    return PAYMENT_RATE;
  }
}
