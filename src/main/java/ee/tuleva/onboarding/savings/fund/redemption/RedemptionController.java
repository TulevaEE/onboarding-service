package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.transfer.iban.ValidIban;
import ee.tuleva.onboarding.currency.Currency;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/savings/redemptions")
@RequiredArgsConstructor
public class RedemptionController {

  private final RedemptionService redemptionService;

  @Operation(summary = "Create redemption request")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RedemptionRequest createRedemption(
      @Valid @RequestBody RedemptionRequestDto request,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    log.info(
        "Creating redemption request: userId={}, amount={}, currency={}, iban={}",
        authenticatedPerson.getUserId(),
        request.amount(),
        request.currency(),
        request.iban());

    return redemptionService.createRedemptionRequest(
        authenticatedPerson.getUserId(), request.amount(), request.currency(), request.iban());
  }

  @Operation(summary = "Cancel redemption request")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelRedemption(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    log.info(
        "Cancelling redemption request: id={}, userId={}", id, authenticatedPerson.getUserId());
    redemptionService.cancelRedemption(id, authenticatedPerson.getUserId());
  }

  public record RedemptionRequestDto(
      @NotNull @Positive BigDecimal amount, @NotNull Currency currency, @ValidIban String iban) {}
}
