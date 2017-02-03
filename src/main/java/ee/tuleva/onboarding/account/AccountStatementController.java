package ee.tuleva.onboarding.account;

import ee.eesti.xtee6.kpr.PensionAccountBalanceResponseType;
import ee.eesti.xtee6.kpr.PensionAccountBalanceType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.kpr.KPRClient;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

    private final KPRClient xRoadClient;
    private final IsinAppender isinAppender;

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalance> getMyPensionAccountStatement(@ApiIgnore @AuthenticationPrincipal User user) {
        /*
        PensionAccountBalanceType request = new PensionAccountBalanceType();
        request.setBalanceDate(null);
        PensionAccountBalanceResponseType response = xRoadClient.pensionAccountBalance(request, user.getPersonalCode());

        List<FundBalance> fbs = KPRUnitsOsakudToFundBalance.convertList(response.getUnits().getBalance());
        fbs = isinAppender.convertList(fbs);

        return fbs;
        */


        List<FundBalance> ret = new ArrayList<FundBalance>();

        ret.add(FundBalance.builder()
                .manager("LHV")
                .isin("EE3600019832")
                .name("LHV Pensionifond L")
                .price(new BigDecimal("25224.22271652"))
                .currency("EUR")
                .build());

        ret.add(FundBalance.builder()
                .manager("SwedBank")
                .isin("EE3600109393")
                .name("Swedbank Pensionifond K90-99 (Elutsükli strateegia)")
                .price(new BigDecimal("424242.56"))
                .currency("EUR")
                .build());

        return ret;
    }
}
