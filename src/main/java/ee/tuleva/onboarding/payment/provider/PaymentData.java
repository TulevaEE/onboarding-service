package ee.tuleva.onboarding.payment.provider;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
class PaymentData {
  private Person person;
  private Currency currency;
  private BigDecimal amount;
  private Bank bank;
}
