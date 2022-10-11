package ee.tuleva.onboarding.payment.provider;

import ee.tuleva.onboarding.payment.Payment;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentProviderCallbackJwtFilter extends BasicAuthenticationFilter {

  private final PaymentProviderCallbackService paymentProviderCallbackService;

  private final JdbcTokenStore tokenStore;

  private final DefaultTokenServices tokenServices;

  public PaymentProviderCallbackJwtFilter(
      AuthenticationManager authenticationManager,
      PaymentProviderCallbackService paymentProviderCallbackService,
      JdbcTokenStore tokenStore,
      DefaultTokenServices tokenServices) {
    super(authenticationManager);
    this.paymentProviderCallbackService = paymentProviderCallbackService;
    this.tokenStore = tokenStore;
    this.tokenServices = tokenServices;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    var authentication = getAuthentication(request);
    if (authentication == null) {
      filterChain.doFilter(request, response);
      return;
    }
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }

  private Authentication getAuthentication(HttpServletRequest request) {
    var token = request.getParameter("payment_token");

    if (StringUtils.isBlank(token)) {
      return null;
    }

    Optional<Payment> maybePayment = paymentProviderCallbackService.processToken(token);

    if (maybePayment.isEmpty()) {
      return null;
    }

    Payment payment = maybePayment.get();

    Collection<OAuth2AccessToken> tokens =
        tokenStore.findTokensByUserName(payment.getPersonalCode());

    if (tokens.isEmpty()) {
      return null;
    }

    val accessToken = tokens.stream().findFirst().get();

    Authentication authentication = tokenServices.loadAuthentication(accessToken.getValue());

    return authentication;
  }
}
