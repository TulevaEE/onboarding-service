package ee.tuleva.onboarding.payment.email;

import static ee.tuleva.onboarding.nudge.NudgeContext.SAVINGS_FUND_PAYMENT;
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_PAYMENT;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.nudge.NudgeAccount;
import ee.tuleva.onboarding.nudge.NudgeDecisionService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCreatedEvent;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentEmailSender {

  private final PaymentEmailService emailService;
  private final SecurityContextRunner securityContextRunner;
  private final NudgeDecisionService nudgeDecisionService;
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
                nudgeDecisionService.decide(event.getUser(), THIRD_PILLAR_PAYMENT),
                event.getLocale()));
  }

  @EventListener
  public void onSavingsPaymentCreated(SavingsPaymentCreatedEvent event) {
    var user = event.getUser();
    var paidAccount = paidAccount(event.getRecipient());
    securityContextRunner.runAs(
        user,
        () ->
            emailService.sendSavingsFundPaymentEmail(
                user,
                savingsFundSuccessEmailResolver.resolve(event),
                nudgeDecisionService.decide(user, paidAccount, SAVINGS_FUND_PAYMENT),
                event.getLocale()));
  }

  @EventListener
  public void onSavingsPaymentCancelled(SavingsPaymentCancelledEvent event) {
    emailService.sendSavingsFundPaymentEmail(
        event.getUser(), SavingsFundPaymentEmail.cancelled(), event.getLocale());
  }

  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Transactional(propagation = REQUIRES_NEW)
  public void onSavingsPaymentFailed(SavingsPaymentFailedEvent event) {
    emailService.sendSavingsFundPaymentEmail(
        event.getUser(), SavingsFundPaymentEmail.failed(), event.getLocale());
  }

  private static NudgeAccount paidAccount(PartyId recipient) {
    RoleType type =
        switch (recipient.type()) {
          case PERSON -> RoleType.PERSON;
          case LEGAL_ENTITY -> RoleType.LEGAL_ENTITY;
        };
    return new NudgeAccount(type, recipient.code());
  }
}
