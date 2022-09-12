package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentData {
  private Currency currency;
  private BigDecimal amount;
  private String internalReference;
  private String paymentInformation;
  private String reference;
  private Bank bank;
  private String firstName;
  private String lastName;
}
