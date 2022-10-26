package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.RECURRING;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentData {

  @NotNull private BigDecimal amount;
  @NotNull private Currency currency;
  @NotNull private PaymentType type;
  @NotNull private Bank bank;

  @JsonIgnore
  public boolean isRecurring() {
    return type == RECURRING;
  }

  public enum PaymentType {
    SINGLE,
    RECURRING
  }

  public enum Bank {
    LUMINOR,
    SEB,
    SWEDBANK,
    LHV
  }
}
