package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;

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
import org.springframework.security.access.AccessDeniedException;
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
    requirePersonRole(authenticatedPerson);
    log.info(
        "Creating redemption request: userId={}, party={}, amount={}, currency={}, iban={}",
        authenticatedPerson.getUserId(),
        authenticatedPerson.toPartyId(),
        request.amount(),
        request.currency(),
        request.iban());

    return redemptionService.createRedemptionRequest(
        authenticatedPerson, request.amount(), request.currency(), request.iban());
  }

  @Operation(summary = "Cancel redemption request")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelRedemption(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    requirePersonRole(authenticatedPerson);
    log.info(
        "Cancelling redemption request: id={}, userId={}, party={}",
        id,
        authenticatedPerson.getUserId(),
        authenticatedPerson.toPartyId());
    redemptionService.cancelRedemption(id, authenticatedPerson);
  }

  private void requirePersonRole(AuthenticatedPerson authenticatedPerson) {
    if (authenticatedPerson.getRoleType() != PERSON) {
      throw new AccessDeniedException(
          "Redemptions for legal entities are not yet supported: party="
              + authenticatedPerson.toPartyId());
    }
  }

  public record RedemptionRequestDto(
      @NotNull @Positive BigDecimal amount, @NotNull Currency currency, @ValidIban String iban) {}
}
