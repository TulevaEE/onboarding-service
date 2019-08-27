package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.fund.response.FundBalanceResponseDto;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

    private final AccountStatementService accountStatementService;

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalanceResponseDto> getMyPensionAccountStatement(
        @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
        @RequestHeader(value = "Accept-Language", defaultValue = "et") String language
    ) {
        List<FundBalance> fundBalances = accountStatementService.getAccountStatement(authenticatedPerson);
        return convertToDtos(fundBalances, language);
    }


    private List<FundBalanceResponseDto> convertToDtos(List<FundBalance> fundBalances, String language) {
        return fundBalances.stream()
            .map(fundBalance -> FundBalanceResponseDto.from(fundBalance, language))
            .collect(toList());
    }
}
