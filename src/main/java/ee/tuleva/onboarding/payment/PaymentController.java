package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.AuthenticatedPersonPrincipal;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Optional;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

  @Value("${frontend.url}")
  private String frontendUrl;

  private final PaymentService paymentService;

  @GetMapping("/link")
  @Operation(summary = "Get a payment link")
  public PaymentLink getPaymentLink(
      @Valid PaymentData paymentData,
      @AuthenticatedPersonPrincipal AuthenticatedPerson authenticatedPerson) {
    return paymentService.getLink(paymentData, authenticatedPerson);
  }

  @GetMapping("/success")
  @Operation(summary = "Redirects user to payment success")
  public RedirectView getPaymentSuccessRedirect(
      @RequestParam("payment_token") String serializedToken) {
    Optional<Payment> payment = paymentService.processToken(serializedToken);
    if (payment.isPresent()) {
      return new RedirectView(frontendUrl + "/3rd-pillar-success");
    }
    return new RedirectView(frontendUrl + "/3rd-pillar-payment");
  }

  @PostMapping("/notifications")
  @Operation(summary = "Payment callback")
  public void paymentCallback(@RequestParam("payment_token") String serializedToken) {
    paymentService.processToken(serializedToken);
  }
}
