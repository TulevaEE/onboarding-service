package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.party.ValidPartyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
@Builder
public class PaymentData {

  // TODO: rename to recipientCode, since it can be a company registration code as well
  @ValidPartyCode @NotNull private String recipientPersonalCode;

  // Optional for MEMBER_FEE and SAVINGS_RECURRING; non-null types validate it in the service layer.
  @Nullable
  @DecimalMin("0.01")
  private BigDecimal amount;

  @Nullable private Currency currency;
  @NotNull private PaymentType type;
  @Nullable private PaymentChannel paymentChannel;

  public enum PaymentType {
    SINGLE,
    RECURRING,
    GIFT,
    MEMBER_FEE,
    SAVINGS,
    SAVINGS_RECURRING,
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
