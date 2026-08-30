package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.analytics.RecurringSavers;
import ee.tuleva.onboarding.analytics.SaverId;
import ee.tuleva.onboarding.analytics.SecondPillarLeavers;
import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.contribution.ThirdPillarTaxHeadroom;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.ContactDetailsService;
import ee.tuleva.onboarding.mandate.PillarSuggestion;
import ee.tuleva.onboarding.mandate.SavingsFundSaverStatus;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.PaymentEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.User;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentEmailSender {

  private final PaymentEmailService emailService;
  private final UserConversionService conversionService;
  private final SecurityContextRunner securityContextRunner;
  private final ContactDetailsService contactDetailsService;
  private final SecondPillarPaymentRateService paymentRateService;
  private final SecondPillarLeavers secondPillarLeavers;
  private final SavingsFundSaverStatus savingsFundSaverStatus;
  private final RecurringSavers recurringSavers;
  private final ThirdPillarTaxHeadroom thirdPillarTaxHeadroom;
  private final SavingsFundSuccessEmailResolver savingsFundSuccessEmailResolver;

  // TODO: can we make these @Async?
  @EventListener
  public void onThirdPillarPaymentCreated(PaymentCreatedEvent event) {
    if (event.getPaymentType() == MEMBER_FEE) {
      return;
    }
    securityContextRunner.runAs(
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
    var recipient = event.getRecipient();
    var saverType =
        switch (recipient.type()) {
          case PERSON -> SaverId.Type.PERSON;
          case LEGAL_ENTITY -> SaverId.Type.LEGAL_ENTITY;
        };
    var saver = new SaverId(saverType, recipient.code());
    boolean suggestAccountRecurringPayment =
        !recurringSavers.hasRecurringSavingsFundPayments(saver);
    sendSavingsFundEmail(
        event, savingsFundSuccessEmailResolver.resolve(event), suggestAccountRecurringPayment);
  }

  @EventListener
  public void onSavingsPaymentCancelled(SavingsPaymentCancelledEvent event) {
    sendSavingsFundEmail(event, SavingsFundPaymentEmail.cancelled(), false);
  }

  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Transactional(propagation = REQUIRES_NEW)
  public void onSavingsPaymentFailed(SavingsPaymentFailedEvent event) {
    emailService.sendSavingsFundPaymentEmail(
        event.getUser(), SavingsFundPaymentEmail.failed(), event.getLocale());
  }

  private void sendSavingsFundEmail(
      PaymentEvent event, SavingsFundPaymentEmail email, boolean suggestAccountRecurringPayment) {
    securityContextRunner.runAs(
        event.getUser(),
        () ->
            emailService.sendSavingsFundPaymentEmail(
                event.getUser(),
                email,
                pillarSuggestionFor(event.getUser()),
                suggestAccountRecurringPayment,
                event.getLocale()));
  }

  private PillarSuggestion pillarSuggestionFor(User user) {
    return pillarSuggestionFor(user, Set.of());
  }

  private PillarSuggestion thirdPillarSuggestionFor(User user) {
    return pillarSuggestionFor(user, Set.of(3));
  }

  private PillarSuggestion pillarSuggestionFor(User user, Set<Integer> concernedPillars) {
    var contactDetails = contactDetailsService.getContactDetails(user);
    return new PillarSuggestion(
        user,
        contactDetails.isSecondPillarActive(),
        contactDetails.isThirdPillarActive(),
        conversionService.getConversion(user),
        paymentRateService.getPaymentRates(user),
        concernedPillars,
        secondPillarLeavers.hasLeft(user.getPersonalCode()),
        savingsFundSaverStatus.isSaver(user.getPersonalCode()),
        false,
        recurringSavers.recurringPaymentsOf(user.getPersonalCode()),
        thirdPillarTaxHeadroom.hasHeadroom(user));
  }
}
