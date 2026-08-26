package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.analytics.RecurringSavers;
import ee.tuleva.onboarding.analytics.SecondPillarLeavers;
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
import ee.tuleva.onboarding.savings.fund.SavingsFundSavers;
import ee.tuleva.onboarding.user.User;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

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
  private final SecondPillarLeavers secondPillarLeavers;
  private final SavingsFundSavers savingsFundSavers;
  private final RecurringSavers recurringSavers;
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
                thirdPillarSuggestionFor(event.getUser()),
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

  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Transactional(propagation = REQUIRES_NEW)
  public void onSavingsPaymentFailed(SavingsPaymentFailedEvent event) {
    emailService.sendSavingsFundPaymentEmail(
        event.getUser(), SavingsFundPaymentEmail.failed(), event.getLocale());
  }

  private void sendSavingsFundEmail(PaymentEvent event, SavingsFundPaymentEmail email) {
    withSecurityContext(
        event.getUser(),
        () ->
            emailService.sendSavingsFundPaymentEmail(
                event.getUser(), email, pillarSuggestionFor(event.getUser()), event.getLocale()));
  }

  private PillarSuggestion pillarSuggestionFor(User user) {
    return pillarSuggestionFor(user, Set.of());
  }

  private PillarSuggestion thirdPillarSuggestionFor(User user) {
    return pillarSuggestionFor(user, Set.of(3));
  }

  private PillarSuggestion pillarSuggestionFor(User user, Set<Integer> concernedPillars) {
    return new PillarSuggestion(
        user,
        contactDetailsService.getContactDetails(user),
        conversionService.getConversion(user),
        paymentRateService.getPaymentRates(user),
        concernedPillars,
        secondPillarLeavers.hasLeft(user.getPersonalCode()),
        savingsFundSavers.isSaver(user.getPersonalCode()),
        false,
        recurringSavers.recurringPaymentsOf(user.getPersonalCode()));
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
