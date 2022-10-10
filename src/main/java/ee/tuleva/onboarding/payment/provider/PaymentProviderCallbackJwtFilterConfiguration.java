package ee.tuleva.onboarding.payment.provider;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;

@Configuration
@RequiredArgsConstructor
public class PaymentProviderCallbackJwtFilterConfiguration
    extends AbstractHttpConfigurer<PaymentProviderCallbackJwtFilterConfiguration, HttpSecurity> {

  private final PaymentProviderCallbackService paymentProviderCallbackService;
  private final JdbcTokenStore tokenStore;

  @Override
  public void configure(HttpSecurity http) {
    val authenticationManager = http.getSharedObject(AuthenticationManager.class);
    val filter =
        new PaymentProviderCallbackJwtFilter(
            authenticationManager, paymentProviderCallbackService, tokenStore);
    http.addFilter(filter);
  }
}
