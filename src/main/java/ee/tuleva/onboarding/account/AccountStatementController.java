package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.fund.response.FundBalanceResponseDto;
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatisticsService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

    public static final boolean CALCULATE_CONTRIBUTION_SUM = true;
    private final AccountStatementService accountStatementService;
    private final FundTransferStatisticsService fundTransferStatisticsService;

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalanceResponseDto> getMyPensionAccountStatement(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                                                     @RequestHeader(value = "x-statistics-identifier", required = false) UUID statisticsIdentifier,
                                                                     @RequestHeader(value = "Accept-Language", defaultValue = "et") String language
    ) {
        List<FundBalance> fundBalances = accountStatementService.getAccountStatement(authenticatedPerson, CALCULATE_CONTRIBUTION_SUM);
        fundTransferStatisticsService.saveFundValueStatistics(fundBalances, statisticsIdentifier);

        return convertToDto(fundBalances, language);
    }


    private List<FundBalanceResponseDto> convertToDto(List<FundBalance> fundBalances, String language) {
        return fundBalances.stream()
            .map(fundBalance -> FundBalanceResponseDto.from(fundBalance, language))
            .collect(toList());
    }
}
