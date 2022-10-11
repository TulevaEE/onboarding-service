package ee.tuleva.onboarding.payment.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@RequiredArgsConstructor
public class PaymentProviderCallbackJwtFilterConfiguration
    extends AbstractHttpConfigurer<PaymentProviderCallbackJwtFilterConfiguration, HttpSecurity> {

  private final PaymentProviderCallbackJwtFilter paymentProviderCallbackJwtFilter;

  @Override
  public void configure(HttpSecurity http) {
    http.addFilter(paymentProviderCallbackJwtFilter);
  }
}
