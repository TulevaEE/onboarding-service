package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioNotificationBody;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @Value("${frontend.url}")
  private String frontendUrl;

  @GetMapping("/link")
  @Operation(summary = "Get a payment link")
  public PaymentLink getPaymentLink(
      @Valid PaymentData paymentData,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return paymentService.getLink(paymentData, authenticatedPerson);
  }

  @GetMapping("/success")
  @Operation(summary = "Redirects user to third pillar payment success")
  public RedirectView getThirdPillarPaymentSuccessRedirect(
      @RequestParam("order-token") String serializedToken) {
    log.info("Processing payment success redirect");
    Optional<Payment> paymentOptional = paymentService.processToken(serializedToken);

    return paymentOptional
        .map(payment -> new RedirectView(frontendUrl + "/3rd-pillar-success"))
        .orElseGet(() -> new RedirectView(frontendUrl + "/3rd-pillar-payment"));
  }

  @GetMapping("/member-success")
  @Operation(summary = "Redirects user to member payment success")
  public RedirectView getMemberPaymentSuccessRedirect(
      @RequestParam("order-token") String serializedToken) {
    log.info("Processing payment member success redirect");
    Optional<Payment> paymentOptional = paymentService.processToken(serializedToken);

    return paymentOptional
        .map(payment -> new RedirectView(frontendUrl))
        .orElseGet(() -> new RedirectView(frontendUrl + "/account"));
  }

  @PostMapping("/notifications")
  @Operation(summary = "Payment callback")
  public void paymentCallback(@RequestBody MontonioNotificationBody montonioNotificationBody) {
    log.info("Processing payment notification token");
    paymentService.processToken(montonioNotificationBody.orderToken());
  }

  @GetMapping("/savings/callback")
  @Operation(summary = "Redirects user to savings fund payment success view")
  public RedirectView getSavingsPaymentReturnRedirect(
      @RequestParam("order-token") String serializedToken) {
    log.info("Processing savings payment return redirect");
    return new RedirectView(frontendUrl + "/savings-fund/payment/success");
  }

  @PostMapping("/savings/notifications")
  @Operation(summary = "Savings fund payment notification callback")
  public void savingsPaymentCallback(
      @RequestBody MontonioNotificationBody montonioNotificationBody) {
    log.info("Processing savings payment notification token");
  }
}
