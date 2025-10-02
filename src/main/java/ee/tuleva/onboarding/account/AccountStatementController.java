package ee.tuleva.onboarding.account;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

  private final AccountStatementService accountStatementService;
  private final SavingsFundStatementService savingsFundStatementService;
  private final LocaleService localeService;

  @Operation(summary = "Get pension register account statement")
  @GetMapping("/pension-account-statement")
  public List<ApiFundBalanceResponse> getMyPensionAccountStatement(
      @RequestParam(value = "from-date", required = false) LocalDate fromDate,
      @RequestParam(value = "to-date", required = false) LocalDate toDate,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    List<FundBalance> fundBalances =
        accountStatementService.getAccountStatement(authenticatedPerson, fromDate, toDate);
    return convertToDtos(fundBalances, localeService.getCurrentLocale());
  }

  @Operation(summary = "Get additional savings fund account statement")
  @GetMapping("/savings-account-statement")
  public ApiFundBalanceResponse getMySavingsAccountStatement(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Optional<FundBalance> fundBalance =
        savingsFundStatementService.getAccountStatement(authenticatedPerson);
    if (fundBalance.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No additional savings fund opened");
    }
    return ApiFundBalanceResponse.from(fundBalance.get(), localeService.getCurrentLocale());
  }

  private List<ApiFundBalanceResponse> convertToDtos(
      List<FundBalance> fundBalances, Locale locale) {
    return fundBalances.stream()
        .map(fundBalance -> ApiFundBalanceResponse.from(fundBalance, locale))
        .collect(toList());
  }
}
