package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/v1/test-ledger")
@AllArgsConstructor
public class LedgerTestController {

  private final UserService userService;
  private final LedgerPartyService ledgerPartyService;
  private final LedgerAccountService ledgerAccountService;

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

  @Operation(summary = "Create account")
  @PostMapping("/account")
  public LedgerAccount createAccount(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);

    LedgerParty ledgerParty =
        ledgerPartyService
            .getPartyForUser(user)
            .orElse(ledgerPartyService.createPartyForUser(user));

    return ledgerAccountService.createAccountForParty(ledgerParty);
  }
}
