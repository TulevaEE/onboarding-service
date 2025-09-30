package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentData {

  @ValidPersonalCode @NotNull private String recipientPersonalCode;
  // TODO: add validation and make it work with MEMBER_FEE type
  // @NotNull @DecimalMin("0.01")
  private BigDecimal amount;
  private Currency currency;
  @NotNull private PaymentType type;
  @NotNull private PaymentChannel paymentChannel;

  public enum PaymentType {
    SINGLE,
    RECURRING,
    GIFT,
    MEMBER_FEE,
    SAVINGS,
  }

  public enum PaymentChannel {
    LUMINOR,
    SEB,
    SWEDBANK,
    LHV,
    COOP,
    COOP_WEB,
    PARTNER, // COOP_APP
    TULUNDUSUHISTU
  }
}
