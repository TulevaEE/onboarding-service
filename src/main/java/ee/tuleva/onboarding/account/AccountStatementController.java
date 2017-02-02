package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.xroad.XRoadClient;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
public class AccountStatementController {

    private XRoadClient xRoadClient;
    private IsinAppender isinAppender;

    @Autowired
    public AccountStatementController(XRoadClient xRoadClient, IsinAppender isinAppender) {
        this.xRoadClient = xRoadClient;
        this.isinAppender = isinAppender;
    }

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalance> getMyPensionAccountStatement(@AuthenticationPrincipal User user) {
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        // todo todays date or null?
        request.setBalanceDate(null);
        PensionAccountBalanceResponseType response = xRoadClient.getPort().pensionAccountBalance(request);

        List<FundBalance> fbs = KPRUnitsOsakudToFundBalance.convertList(response.getUnits().getBalance());
        fbs = isinAppender.convertList(fbs);

        return fbs;
    }
}
