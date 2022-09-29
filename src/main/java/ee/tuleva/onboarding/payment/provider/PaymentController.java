package ee.tuleva.onboarding.payment.provider;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.currency.Currency;
import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
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
class PaymentController {

  @Value("${frontend.url}")
  private String frontendUrl;

  private final PaymentProviderService paymentProviderService;

  private final PaymentProviderCallbackService paymentProviderCallbackService;

  @GetMapping("/link")
  @Operation(summary = "Create a payment")
  public PaymentLink createPayment(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam Currency currency,
      @RequestParam BigDecimal amount,
      @RequestParam Bank bank) {

    PaymentData paymentData =
        PaymentData.builder()
            .person(authenticatedPerson)
            .currency(currency)
            .amount(amount)
            .bank(bank)
            .build();

    return paymentProviderService.getPaymentLink(paymentData);
  }

  @GetMapping("/success")
  @Operation(summary = "Redirects user to payment success")
  public RedirectView getPaymentSuccessRedirect(
      @RequestParam("payment_token") String serializedToken) {
    paymentProviderCallbackService.processToken(serializedToken);
    return new RedirectView(frontendUrl + "/3rd-pillar-flow/success");
  }

  @PostMapping("/notifications")
  @Operation(summary = "Payment callback")
  public void paymentCallback(@RequestParam("payment_token") String serializedToken) {
    paymentProviderCallbackService.processToken(serializedToken);
  }
}
