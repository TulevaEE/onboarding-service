package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.fund.response.FundResponse;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    @ApiOperation(value = "Get info about available funds")
    @RequestMapping(method = GET, value = "/funds")
    public List<FundResponse> get(@RequestParam("fundManager.name") Optional<String> fundManagerName) {
        return fundService.getFunds(fundManagerName);
    }

}
