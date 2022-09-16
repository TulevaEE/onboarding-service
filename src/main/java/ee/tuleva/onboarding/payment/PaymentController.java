package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.currency.Currency;
import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

  @Value("${frontend.url}")
  private String frontendUrl;

  private final PaymentProviderService paymentProviderService;

  @GetMapping("/link")
  @Operation(summary = "Create a payment")
  public String createPayment(
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

    String paymentUrl = paymentProviderService.getPaymentUrl(paymentData);
    return "redirect:" + paymentUrl;
  }

  @GetMapping("/success")
  @Operation(summary = "Redirects user to payment success")
  public String getPaymentSuccessRedirect(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return "redirect:" + frontendUrl + "/3rd-pillar-flow/success/";
  }

  @PostMapping("/notifications")
  @Operation(summary = "Payment callback")
  public void paymentCallback() {}
}
