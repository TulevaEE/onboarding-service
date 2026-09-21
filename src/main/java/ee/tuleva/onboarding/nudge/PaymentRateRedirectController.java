package ee.tuleva.onboarding.nudge;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/payment-rate-redirect")
@RequiredArgsConstructor
public class PaymentRateRedirectController {

  private final PaymentRateRedirectService paymentRateRedirectService;

  @Operation(summary = "Whether to send the person who just logged in to the payment rate page")
  @PostMapping
  public PaymentRateRedirect redirect(@AuthenticationPrincipal AuthenticatedPerson person) {
    return paymentRateRedirectService.assign(person);
  }

  @Operation(summary = "The person dismissed the payment rate page they were sent to")
  @PostMapping("/dismissal")
  @ResponseStatus(NO_CONTENT)
  public void dismiss(@AuthenticationPrincipal AuthenticatedPerson person) {
    paymentRateRedirectService.dismiss(person);
  }
}
