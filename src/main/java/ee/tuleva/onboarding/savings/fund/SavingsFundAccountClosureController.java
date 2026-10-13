package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/savings/account-closure")
@RequiredArgsConstructor
class SavingsFundAccountClosureController {

  private final SavingsFundAccountClosureService closureService;

  @Operation(summary = "Tell whether the person's own savings fund account can be closed")
  @GetMapping
  AccountClosure getAccountClosure(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return new AccountClosure(closureService.isClosable(authenticatedPerson));
  }

  @Operation(summary = "Close the person's own empty savings fund account")
  @PostMapping
  ResponseEntity<Void> closeAccount(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    closureService.close(authenticatedPerson);
    return ResponseEntity.noContent().build();
  }

  record AccountClosure(boolean closable) {}
}
