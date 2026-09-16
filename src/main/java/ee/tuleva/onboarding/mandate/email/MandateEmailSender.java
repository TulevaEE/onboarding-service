package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.nudge.NudgeContext.SECOND_PILLAR_MANDATE;
import static ee.tuleva.onboarding.nudge.NudgeContext.SECOND_PILLAR_PAYMENT_RATE;
import static ee.tuleva.onboarding.nudge.NudgeContext.THIRD_PILLAR_MANDATE;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.nudge.NudgeContext;
import ee.tuleva.onboarding.nudge.NudgeDecision;
import ee.tuleva.onboarding.nudge.NudgeDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MandateEmailSender {

  private final MandateEmailService mandateEmailService;
  private final NudgeDecisionService nudgeDecisionService;

  @EventListener
  public void sendEmail(AfterMandateSignedEvent event) {
    Mandate mandate = event.getMandate();
    if (mandate.isPartOfBatch()) {
      log.info(
          "Skipping mandate email because it is part of a batch: mandateId={}", mandate.getId());
      return;
    }
    NudgeDecision decision = nudgeDecisionService.decide(event.getUser(), contextFor(mandate));
    mandateEmailService.sendMandate(event.getUser(), mandate, decision, event.getLocale());
  }

  static NudgeContext contextFor(Mandate mandate) {
    if (mandate.isPaymentRateApplication()) {
      return SECOND_PILLAR_PAYMENT_RATE;
    }
    return mandate.getPillar() == 3 ? THIRD_PILLAR_MANDATE : SECOND_PILLAR_MANDATE;
  }
}
