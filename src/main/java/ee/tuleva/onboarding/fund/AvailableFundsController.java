package ee.tuleva.onboarding.fund;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.domain.fund.*;
import ee.tuleva.onboarding.user.User;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AvailableFundsController {

    private final FundRepository fundRepository;
    private final FundManagerRepository fundManagerRepository;

    @ApiOperation(value = "Get info about available funds")
    @RequestMapping(method = GET, value = "/available-funds")
    @JsonView(FundView.SkipFundManager.class)
    public List<Fund> initialCapital(@AuthenticationPrincipal User user) {
        //FIXME: extract into a service
        FundManager tulevaFundManager = fundManagerRepository.findByName("Tuleva");
        return fundRepository.findByFundManager(tulevaFundManager);
    }

}
