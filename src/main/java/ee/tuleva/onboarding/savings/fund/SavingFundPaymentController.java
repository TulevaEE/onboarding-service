package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.event.SavingsPaymentCancelledEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/savings")
@RequiredArgsConstructor
public class SavingFundPaymentController {

  private final UserService userService;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final SavingFundPaymentUpsertionService savingFundPaymentUpsertionService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final LocaleService localeService;
  private final ApplicationEventPublisher eventPublisher;

  @Operation(summary = "Cancel savings fund payment")
  @DeleteMapping("/payments/{id}")
  public ResponseEntity<Void> cancelSavingsFundPayment(
      @PathVariable("id") UUID paymentId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    log.info("Cancelling savings fund payment {}", paymentId);
    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    savingFundPaymentUpsertionService.cancelUserPayment(user.getId(), paymentId);
    eventPublisher.publishEvent(
        new SavingsPaymentCancelledEvent(this, user, localeService.getCurrentLocale()));
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Get user savings fund onboarding status")
  @GetMapping("/onboarding/status")
  public Map<String, Optional<SavingsFundOnboardingStatus>> getSavingsFundOnboardingStatus(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    SavingsFundOnboardingStatus status =
        savingsFundOnboardingService.getOnboardingStatus(
            userService.getByIdOrThrow(authenticatedPerson.getUserId()));
    return Map.of("status", Optional.ofNullable(status));
  }

  @Operation(summary = "Get user bank accounts used for deposits")
  @GetMapping("/bank-accounts")
  public List<String> getBankAccounts(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    return savingFundPaymentRepository.findUserDepositBankAccountIbans(user.getId());
  }
}
