package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/savings")
@RequiredArgsConstructor
public class SavingFundPaymentController {

  private final SavingFundPaymentUpsertionService savingFundPaymentUpsertionService;

  @Operation(summary = "Cancel savings fund payment")
  @DeleteMapping("/payments/{id}")
  public ResponseEntity<Void> cancelSavingsFundPayment(
      @PathVariable("id") UUID paymentId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    log.info("Cancelling savings fund payment {}", paymentId);
    savingFundPaymentUpsertionService.cancelUserPayment(authenticatedPerson.getUserId(), paymentId);
    return ResponseEntity.noContent().build();
  }
}
