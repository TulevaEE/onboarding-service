package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.eesti.xtee6.kpr.PersonalSelectionResponseType;
import ee.tuleva.domain.fund.Fund;
import ee.tuleva.domain.fund.FundRepository;
import ee.tuleva.onboarding.kpr.KPRClient;
import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

    private final KPRClient kprClient;
    private final IsinAppender isinAppender;
    private final FundRepository fundRepository;

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalance> getMyPensionAccountStatement(@ApiIgnore @AuthenticationPrincipal User user) {
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        request.setBalanceDate(null);
        PensionAccountBalanceResponseType response = kprClient.pensionAccountBalance(request, user.getPersonalCode());

        List<FundBalance> fbs = KPRUnitsOsakudToFundBalance.convertList(response.getUnits().getBalance());
        fbs = isinAppender.convertList(fbs);

        // assembling active fund bit and managementFeeRate to here.
        PersonalSelectionResponseType csdPersonalSelection = kprClient.personalSelection(user.getPersonalCode());
        String activeFundName = csdPersonalSelection.getPensionAccount().getSecurityName();

        // there are rare moments when active fund doesn't have the balance
        boolean containsActiveFund = false;

        for (FundBalance balance : fbs) {
            if (balance.getName().equals(activeFundName)) {
                balance.setActiveFund(true);
                containsActiveFund = true;
            }
        }

        if (!containsActiveFund) {
            FundBalance activeFundBalance = FundBalance.builder()
                    .name(activeFundName)
                    .price(BigDecimal.ZERO)
                    .currency("EUR")
                    .build();

            Fund activeFund = fundRepository.findByNameIgnoreCase(activeFundName);
            if (activeFund != null) {
                activeFundBalance.setIsin(activeFund.getIsin());
                activeFundBalance.setManagementFeeRate(activeFund.getManagementFeeRate());
            }

            fbs.add(activeFundBalance);
        }

        return fbs;
    }
}
