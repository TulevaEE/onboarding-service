package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.KprV6PortType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.tuleva.ee.tuleva.onboarding.xroad.XRoadClient;
import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
public class AccountStatementController {
    /*
    private XRoadClient xRoadClient;

    @Autowired
    AccountStatementController(XRoadClient xRoadClient) {
        this.xRoadClient = xRoadClient;
    }
    */

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalance> getMyPensionAccountStatement(@AuthenticationPrincipal User user) {

        //PensionAccountBalanceType request = new PensionAccountBalanceType();
        //request.setBalanceDate(null);
        //PensionAccountBalanceResponseType response = xRoadClient.getPort().pensionAccountBalance(request);
        //response.getUnits();


        return Arrays.asList(
                FundBalance.builder()
                        .isin("EE1234567890")
                        .name("My fund")
                        .manager("Myself")
                        .price(new BigDecimal("10.03"))
                        .currency("EUR")
                        .build(),
                FundBalance.builder()
                        .isin("EE0987654321")
                        .name("Friends fund")
                        .manager("Buddy")
                        .price(new BigDecimal("19.04"))
                        .currency("EUR")
                        .build()
        );
    }
}
