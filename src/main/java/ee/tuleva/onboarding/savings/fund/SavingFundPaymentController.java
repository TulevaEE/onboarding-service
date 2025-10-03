package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
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

  private final UserService userService;
  private final SavingFundPaymentUpsertionService savingFundPaymentUpsertionService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;

  @Operation(summary = "Cancel savings fund payment")
  @DeleteMapping("/payments/{id}")
  public ResponseEntity<Void> cancelSavingsFundPayment(
      @PathVariable("id") UUID paymentId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    log.info("Cancelling savings fund payment {}", paymentId);
    savingFundPaymentUpsertionService.cancelUserPayment(authenticatedPerson.getUserId(), paymentId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Get user savings fund onboarding status")
  @GetMapping("/onboarding/status")
  public Map<String, SavingsFundOnboardingStatus> getSavingsFundOnboardingStatus(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SavingsFundOnboardingStatus status =
        savingsFundOnboardingService.getOnboardingStatus(
            userService.getByIdOrThrow(authenticatedPerson.getUserId()));
    return Map.of("status", status);
  }
}
