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
    TWO(BigDecimal.valueOf(2.0)),
    FOUR(BigDecimal.valueOf(4.0)),
    SIX(BigDecimal.valueOf(6.0));

    private final BigDecimal numericValue;

    PaymentRate(BigDecimal numericValue) {
      this.numericValue = numericValue;
    }

    public static PaymentRate fromValue(BigDecimal value) {
      return Arrays.stream(values())
          .filter(rate -> rate.numericValue.equals(value))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("No payment rate found"));
    }
  }

  @Override
  public ApplicationType getApplicationType() {
    return PAYMENT_RATE;
  }
}
