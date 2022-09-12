package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.currency.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePaymentCommand {

  private BigDecimal amount;

  private Currency currency;

  private Bank bank;

}
