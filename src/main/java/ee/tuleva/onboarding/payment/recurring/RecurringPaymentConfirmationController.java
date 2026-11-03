package ee.tuleva.onboarding.payment.recurring;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class RecurringPaymentConfirmationController {

  private final ThirdPillarStandingOrders standingOrders;

  @Operation(summary = "The person confirmed setting up a III pillar recurring payment")
  @PostMapping("/v1/me/third-pillar/recurring-payment-confirmations")
  @ResponseStatus(NO_CONTENT)
  public void confirm(@AuthenticationPrincipal AuthenticatedPerson person) {
    standingOrders.recordConfirmation(person);
  }
}
