package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.mandate.email.PillarSuggestion;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.PaymentEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
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
  private final SavingsFundSuccessEmailResolver savingsFundSuccessEmailResolver;

  // TODO: can we make these @Async?
  @EventListener
  public void onThirdPillarPaymentCreated(PaymentCreatedEvent event) {
    if (event.getPaymentType() == MEMBER_FEE) {
      return;
    }
    withSecurityContext(
        event.getUser(),
        () ->
            emailService.sendThirdPillarPaymentSuccessEmail(
                event.getUser(),
                event.getPayment(),
                pillarSuggestionFor(event.getUser()),
                event.getLocale()));
  }

  @EventListener
  public void onSavingsPaymentCreated(SavingsPaymentCreatedEvent event) {
    sendSavingsFundEmail(event, savingsFundSuccessEmailResolver.resolve(event));
  }

  @EventListener
  public void onSavingsPaymentCancelled(SavingsPaymentCancelledEvent event) {
    sendSavingsFundEmail(event, SavingsFundPaymentEmail.cancelled());
  }

  @EventListener
  public void onSavingsPaymentFailed(SavingsPaymentFailedEvent event) {
    sendSavingsFundEmail(event, SavingsFundPaymentEmail.failed());
  }

  private void sendSavingsFundEmail(PaymentEvent event, SavingsFundPaymentEmail email) {
    withSecurityContext(
        event.getUser(),
        () ->
            emailService.sendSavingsFundPaymentEmail(
                event.getUser(), email, pillarSuggestionFor(event.getUser()), event.getLocale()));
  }

  private PillarSuggestion pillarSuggestionFor(User user) {
    return new PillarSuggestion(
        user,
        contactDetailsService.getContactDetails(user),
        conversionService.getConversion(user),
        paymentRateService.getPaymentRates(user));
  }

  private void withSecurityContext(User user, Runnable action) {
    try {
      setupSecurityContext(user);
      action.run();
    } finally {
      SecurityContextHolder.clearContext();
    }
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
