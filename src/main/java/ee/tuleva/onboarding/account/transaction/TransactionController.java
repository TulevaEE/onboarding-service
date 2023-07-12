package ee.tuleva.onboarding.account.transaction;

import static java.util.Comparator.reverseOrder;

import ee.tuleva.onboarding.account.CashFlowService;
import ee.tuleva.onboarding.auth.AuthenticatedPersonPrincipal;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
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

  private final CashFlowService cashFlowService;

  @GetMapping("/transactions")
  @Operation(summary = "Get a list of transactions")
  public List<Transaction> getTransactions(
      @AuthenticatedPersonPrincipal AuthenticatedPerson authenticatedPerson) {
    CashFlowStatement cashFlowStatement = cashFlowService.getCashFlowStatement(authenticatedPerson);
    return cashFlowStatement.getTransactions().stream()
        .filter(cashFlow -> cashFlow.isContribution() || cashFlow.isSubtraction())
        .map(Transaction::from)
        .sorted(reverseOrder())
        .toList();
  }
}
