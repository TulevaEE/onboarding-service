package ee.tuleva.onboarding.payment;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "payment-provider")
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderConfiguration {

  private final Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations;

//  private Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurationsByBic;


  public PaymentProviderBankConfiguration getByBank(Bank bank) {
    return paymentProviderBankConfigurations.get(bank.name().toLowerCase(Locale.ROOT));
  }

//  public PaymentProviderConfiguration getByBic(String bic) {
//    return paymentProviderBankConfigurations.
//  }


}
