package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.mandate.email.PillarSuggestion;
import ee.tuleva.onboarding.payment.Payment;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEmailSender {

  private final PaymentEmailService emailService;
  private final UserConversionService conversionService;
  private final PrincipalService principalService;
  private final GrantedAuthorityFactory grantedAuthorityFactory;
  private final JwtTokenUtil jwtTokenUtil;
  private final ContactDetailsService contactDetailsService;
  private final SecondPillarPaymentRateService paymentRateService;

  @EventListener
  public void sendEmails(PaymentCreatedEvent event) {
    User user = event.getUser();
    Locale locale = event.getLocale();
    Payment payment = event.getPayment();

    if (event.getPaymentType() == MEMBER_FEE) {
      return;
    }

    try {
      setupSecurityContext(user);

      ContactDetails contactDetails = contactDetailsService.getContactDetails(user);
      ConversionResponse conversion = conversionService.getConversion(user);
      PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());

      PillarSuggestion pillarSuggestion =
          new PillarSuggestion(user, contactDetails, conversion, paymentRates);

      emailService.sendThirdPillarPaymentSuccessEmail(user, payment, pillarSuggestion, locale);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void setupSecurityContext(User user) {
    final var principal = principalService.getFrom(user, Map.of());
    final var authorities = grantedAuthorityFactory.from(principal);
    final var jwtToken = jwtTokenUtil.generateToken(principal, authorities);

    final var authenticationToken =
        new UsernamePasswordAuthenticationToken(principal, jwtToken, authorities);

    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
