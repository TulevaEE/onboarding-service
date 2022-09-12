package ee.tuleva.onboarding.payment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentData {
  private String currency;

  private BigDecimal amount;
  private String internalReference;
  private String paymentInformation;
  private Bank bank;
  private String userEmail;
}
