package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentData {

  @ValidPersonalCode @NotNull private String recipientPersonalCode;
  @NotNull private BigDecimal amount;
  @NotNull private Currency currency;
  @NotNull private PaymentType type;
  @NotNull private Bank bank;

  public enum PaymentType {
    SINGLE,
    RECURRING,
    GIFT
  }

  public enum Bank {
    LUMINOR,
    SEB,
    SWEDBANK,
    LHV,
    COOP
  }
}
