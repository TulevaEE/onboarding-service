package ee.tuleva.onboarding.payment.recurring;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/third-pillar/recurring-payment")
@RequiredArgsConstructor
class ThirdPillarRecurringPaymentController {

  private final ThirdPillarStandingOrders standingOrders;

  @GetMapping
  @Operation(summary = "Whether the person already pays into the third pillar by standing order")
  public ThirdPillarRecurringPaymentResponse recurringPayment(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return new ThirdPillarRecurringPaymentResponse(
        standingOrders.paysByStandingOrder(person.getRepresentedPersonalCode()));
  }
}
