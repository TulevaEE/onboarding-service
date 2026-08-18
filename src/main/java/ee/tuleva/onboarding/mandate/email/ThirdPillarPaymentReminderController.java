package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/third-pillar-payment-reminders")
@RequiredArgsConstructor
class ThirdPillarPaymentReminderController {

  private final EmailPersistenceService emailPersistenceService;

  @PostMapping("/cancellations")
  public CancellationResponse cancel(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    var cancelledEmails =
        emailPersistenceService.cancel(authenticatedPerson, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE);
    return new CancellationResponse(cancelledEmails.size());
  }

  record CancellationResponse(int cancelledCount) {}
}
