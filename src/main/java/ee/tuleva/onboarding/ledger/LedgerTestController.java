package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/v1/test-ledger")
@AllArgsConstructor
public class LedgerTestController {

  private final UserService userService;
  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;
  private final LedgerService ledgerService;
  private final SwedbankGatewayClient swedbankGatewayClient;

  @Operation(summary = "Get my ledger accounts")
  @GetMapping("/account")
  public List<LedgerAccount> getAccounts(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);

    LedgerParty ledgerParty =
        ledgerPartyService
            .getPartyForUser(user)
            .orElseThrow(() -> new RuntimeException("No ledger party found for user " + userId));

    return ledgerAccountService.getAccountsByLedgerParty(ledgerParty);
  }

  @Operation(summary = "Onboard user")
  @PostMapping("/onboard")
  public List<LedgerAccount> onboardUser(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);

    return ledgerService.onboardUser(user);
  }

  @Operation(summary = "Deposit")
  @PostMapping("/deposit")
  public LedgerTransaction onboardUser(
      @Valid @RequestBody DepositDto depositDto,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);

    return ledgerService.deposit(user, depositDto.amount(), EUR);
  }

  @Operation(summary = "Swedbank Gateway pong")
  @GetMapping("/swedbank/pong")
  public void swedbankGatewayPong(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {

    swedbankGatewayClient.sendPong();
  }

  record DepositDto(BigDecimal amount) {}
}
