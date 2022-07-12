package ee.tuleva.onboarding.account;

import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

  private final AccountStatementService accountStatementService;
  private final LocaleService localeService;

  @Operation(summary = "Get pension register account statement")
  @RequestMapping(method = GET, value = "/pension-account-statement")
  public List<ApiFundBalanceResponse> getMyPensionAccountStatement(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    List<FundBalance> fundBalances =
        accountStatementService.getAccountStatement(authenticatedPerson);
    return convertToDtos(fundBalances, localeService.getLanguage());
  }

  private List<ApiFundBalanceResponse> convertToDtos(
      List<FundBalance> fundBalances, String language) {
    return fundBalances.stream()
        .map(fundBalance -> ApiFundBalanceResponse.from(fundBalance, language))
        .collect(toList());
  }
}
