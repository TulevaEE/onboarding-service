package ee.tuleva.onboarding.account;

import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.UUID;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AccountStatementController {

    private final AccountStatementService accountStatementService;

    @ApiOperation(value = "Get pension register account statement")
    @RequestMapping(method = GET, value = "/pension-account-statement")
    public List<FundBalance> getMyPensionAccountStatement(@ApiIgnore @AuthenticationPrincipal User user,
                                                          @RequestHeader(value = "x-statistics-identifier", required = false) UUID statisticsIdentifier) {
        return accountStatementService.getMyPensionAccountStatement(user, statisticsIdentifier);
    }
}
