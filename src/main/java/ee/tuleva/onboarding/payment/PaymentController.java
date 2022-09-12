package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
@RequestMapping("/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

  @Value("${frontend.url}")
  private String frontendUrl;

  private final PaymentProviderService paymentProviderService;
  private final EpisService episService;
  private final PaymentInternalReferenceService paymentInternalReferenceService;

  @GetMapping("/link")
  @Operation(summary = "Create a payment")
  public String createPayment(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam Currency currency,
      @RequestParam BigDecimal amount,
      @RequestParam Bank bank
  ) {

    ContactDetails contactDetails = episService.getContactDetails(authenticatedPerson);

    PaymentData paymentData = PaymentData.builder()
        .paymentInformation("30101119828")
        .currency(currency)
        .amount(amount)
        .internalReference(paymentInternalReferenceService.getPaymentReference(authenticatedPerson))
        .bank(bank)
        .firstName(authenticatedPerson.getFirstName())
        .lastName(authenticatedPerson.getLastName())
        .reference(contactDetails.getPensionAccountNumber())
        .build();

    String paymentUrl = paymentProviderService.getPaymentUrl(paymentData);
    return "redirect:" + paymentUrl;
  }

  @GetMapping("/success")
  @Operation(summary = "Redirects user to payment success")
  public String getPaymentSuccessRedirect(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson
  ) {
    return "redirect:" + frontendUrl + "/3rd-pillar-flow/success/";
  }

  @PostMapping("/notifications")
  @Operation(summary = "Payment callback")
  public void paymentCallback() {

  }

}
