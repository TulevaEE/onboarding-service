package ee.tuleva.onboarding.account.transaction;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @GetMapping("/transactions")
  @Operation(summary = "Get a list of transactions")
  public List<Transaction> getTransactions(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return transactionService.getTransactions(authenticatedPerson);
  }
}
