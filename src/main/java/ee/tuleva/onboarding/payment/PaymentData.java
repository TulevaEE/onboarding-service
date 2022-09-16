package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentData {
  private Currency currency;
  private BigDecimal amount;
  private String internalReference;
  private String description;
  private String reference;
  private Bank bank;
  private String firstName;
  private String lastName;
}
