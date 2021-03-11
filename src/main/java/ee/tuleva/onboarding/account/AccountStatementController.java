package ee.tuleva.onboarding.account;

import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.fund.response.FundBalanceResponseDto;
import ee.tuleva.onboarding.locale.LocaleService;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

  private final AccountStatementService accountStatementService;
  private final LocaleService localeService;

  @ApiOperation(value = "Get pension register account statement")
  @RequestMapping(method = GET, value = "/pension-account-statement")
  public List<FundBalanceResponseDto> getMyPensionAccountStatement(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    List<FundBalance> fundBalances =
        accountStatementService.getAccountStatement(authenticatedPerson);
    return convertToDtos(fundBalances, localeService.getLanguage());
  }

  private List<FundBalanceResponseDto> convertToDtos(
      List<FundBalance> fundBalances, String language) {
    return fundBalances.stream()
        .map(fundBalance -> FundBalanceResponseDto.from(fundBalance, language))
        .collect(toList());
  }
}
