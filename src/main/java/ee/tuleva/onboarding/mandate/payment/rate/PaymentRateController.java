package ee.tuleva.onboarding.mandate.payment.rate;

import static ee.tuleva.onboarding.mandate.payment.rate.PaymentRateController.*;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.Mandate;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1" + PAYMENT_RATES_URI)
@RequiredArgsConstructor
class PaymentRateController {

  public static final String PAYMENT_RATES_URI = "/second-pillar-payment-rates";

  private final PaymentRateService paymentRateService;

  @Operation(summary = "Set second pillar payment rate")
  @PostMapping
  public PaymentRateResponse update(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody PaymentRateCommand paymentRateCommand) {
    log.info(
        "Setting user {} second pillar payment rate {}",
        authenticatedPerson.getUserId(),
        paymentRateCommand.getPaymentRate());
    Mandate savedMandate =
        paymentRateService.savePaymentRateMandate(
            authenticatedPerson, paymentRateCommand.getPaymentRate());
    return PaymentRateResponse.builder().mandateId(savedMandate.getId()).build();
  }
}
