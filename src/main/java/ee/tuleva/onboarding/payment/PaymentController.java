package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

@RestController
@RequestMapping("/v1/payment")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

  @Value("${frontend.url}")
  private String frontendUrl;

  @PostMapping
  @Operation(summary = "Create a payment")
  public void createPayment(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody CreatePaymentCommand createPaymentCommand) {

  }

  @GetMapping("/success")
  @Operation(summary = "Redirects user to payment success")
  public void getPaymentSuccessRedirect(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Parameter(hidden = true) HttpServletResponse response
  ) throws IOException {
    response.sendRedirect(frontendUrl + "/3rd-pillar-flow/success/");
//    return "redirect:/3rd-pillar-flow/success/";
  }

}
