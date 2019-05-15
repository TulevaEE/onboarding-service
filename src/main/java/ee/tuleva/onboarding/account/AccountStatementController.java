package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.common.Utils;
import ee.tuleva.onboarding.comparisons.overview.AccountOverview;
import ee.tuleva.onboarding.comparisons.overview.EpisAccountOverviewProvider;
import ee.tuleva.onboarding.comparisons.overview.Transaction;
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatisticsService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

    private final AccountStatementService accountStatementService;
    private final FundTransferStatisticsService fundTransferStatisticsService;
    private final EpisAccountOverviewProvider episAccountOverviewProvider;
    private static final Instant START_TIME = Utils.parseInstant("2002-01-01");

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalance> getMyPensionAccountStatement(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                                          @RequestHeader(value = "x-statistics-identifier", required = false) UUID statisticsIdentifier) {
        List<FundBalance> fundBalances = accountStatementService.getAccountStatement(authenticatedPerson);

        //Calculate total contribution for each fund balance
        fundBalances.stream().forEach(fundBalance -> {
                AccountOverview accountOverview = episAccountOverviewProvider.getAccountOverview(authenticatedPerson, START_TIME, fundBalance.getPillar());

                BigDecimal sumOfAllContributions = accountOverview.getTransactions().stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                fundBalance.setContributionSum(sumOfAllContributions);
            });

        fundTransferStatisticsService.saveFundValueStatistics(fundBalances, statisticsIdentifier);



        return fundBalances;
    }
}
