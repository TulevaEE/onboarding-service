package ee.tuleva.onboarding.savings.fund.taxreport;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InvestmentAccountController {

  private static final String NOT_A_NATURAL_PERSON = "investmentAccount.naturalPersonOnly";

  private final InvestmentAccountService investmentAccountService;

  @Operation(summary = "Get the investment account someone has declared to us")
  @GetMapping("/savings-fund/investment-account")
  public InvestmentAccountResponse getInvestmentAccount(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return new InvestmentAccountResponse(
        investmentAccountService.declaredIban(naturalPersonCodeOf(person)).orElse(null));
  }

  @Operation(summary = "Declare which account is an investment account")
  @PutMapping("/savings-fund/investment-account")
  public InvestmentAccountResponse declareInvestmentAccount(
      @AuthenticationPrincipal AuthenticatedPerson person,
      @Valid @RequestBody InvestmentAccountCommand command) {
    return new InvestmentAccountResponse(
        investmentAccountService.declare(naturalPersonCodeOf(person), command.iban()).getIban());
  }

  @Operation(summary = "Take back a declared investment account")
  @DeleteMapping("/savings-fund/investment-account")
  public InvestmentAccountResponse forgetInvestmentAccount(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    investmentAccountService.forget(naturalPersonCodeOf(person));
    return new InvestmentAccountResponse(null);
  }

  private static String naturalPersonCodeOf(AuthenticatedPerson person) {
    if (person.isLegalEntity()) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(
              NOT_A_NATURAL_PERSON, "Only a natural person can hold an investment account"));
    }
    return person.getRoleCode();
  }
}
