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
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.PaymentEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
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

  // TODO: can we make this @Async?
  @EventListener
  public void sendEmails(PaymentEvent event) {
    if (event.getPaymentType() == MEMBER_FEE) {
      return;
    }

    try {
      setupSecurityContext(event.getUser());
      if (PaymentData.PaymentType.SAVINGS.equals(event.getPaymentType())) {
        sendSavingsFundEmail(event);
      } else {
        sendThirdPillarEmail((PaymentCreatedEvent) event);
      }

    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private EmailType getEmailTypeForSavingsFundEvent(PaymentEvent event) {
    if (event instanceof SavingsPaymentFailedEvent) {
      return EmailType.SAVINGS_FUND_PAYMENT_FAIL;
    } else if (event instanceof SavingsPaymentCancelledEvent) {
      return EmailType.SAVINGS_FUND_PAYMENT_CANCEL;
    }
    return EmailType.SAVINGS_FUND_PAYMENT_SUCCESS;
  }

  private void sendSavingsFundEmail(PaymentEvent event) {
    User user = event.getUser();
    ContactDetails contactDetails = contactDetailsService.getContactDetails(user);
    ConversionResponse conversion = conversionService.getConversion(user);
    PaymentRates paymentRates = paymentRateService.getPaymentRates(user);

    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(user, contactDetails, conversion, paymentRates);

    EmailType emailType = getEmailTypeForSavingsFundEvent(event);
    emailService.sendSavingsFundPaymentEmail(user, emailType, pillarSuggestion, event.getLocale());
  }

  private void sendThirdPillarEmail(PaymentCreatedEvent event) {
    User user = event.getUser();
    ContactDetails contactDetails = contactDetailsService.getContactDetails(user);
    ConversionResponse conversion = conversionService.getConversion(user);
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());

    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(user, contactDetails, conversion, paymentRates);

    emailService.sendThirdPillarPaymentSuccessEmail(
        user, event.getPayment(), pillarSuggestion, event.getLocale());
  }

  private void setupSecurityContext(User user) {
    final var principal = principalService.getFrom(user, Map.of());
    final var authorities = grantedAuthorityFactory.from(principal);
    final var accessToken = jwtTokenUtil.generateAccessToken(principal, authorities);

    final var authenticationToken =
        new UsernamePasswordAuthenticationToken(principal, accessToken, authorities);

    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
