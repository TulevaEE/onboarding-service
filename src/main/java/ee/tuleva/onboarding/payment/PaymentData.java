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
  private BigDecimal amount;
  private Currency currency;
  @NotNull private PaymentType type;
  @NotNull private PaymentChannel paymentChannel;

  public enum PaymentType {
    SINGLE,
    RECURRING,
    GIFT,
    MEMBER_FEE
  }

  public enum PaymentChannel {
    LUMINOR,
    SEB,
    SWEDBANK,
    LHV,
    COOP,
    PARTNER,
    TULUNDUSUHISTU
  }
}
