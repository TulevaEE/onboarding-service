package ee.tuleva.onboarding.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "payment-provider.lhv")
  public PaymentProviderBankConfiguration paymentProviderLhvConfiguration() {
    return new PaymentProviderBankConfiguration();
  }

  @Bean
  @ConfigurationProperties(prefix = "payment-provider.luminor")
  public PaymentProviderBankConfiguration paymentProviderLuminorConfiguration() {
    return new PaymentProviderBankConfiguration();
  }

  @Bean
  @ConfigurationProperties(prefix = "payment-provider.seb")
  public PaymentProviderBankConfiguration paymentProviderSebConfiguration() {
    return new PaymentProviderBankConfiguration();
  }

  @Bean
  @ConfigurationProperties(prefix = "payment-provider.swedbank")
  public PaymentProviderBankConfiguration paymentProviderSwedbankConfiguration() {
    return new PaymentProviderBankConfiguration();
  }

}
