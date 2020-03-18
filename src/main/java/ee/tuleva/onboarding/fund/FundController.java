package ee.tuleva.onboarding.fund;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import ee.tuleva.onboarding.fund.response.FundResponse;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundController {

  private final FundService fundService;

  @ApiOperation(value = "Get info about available funds")
  @RequestMapping(method = GET, value = "/funds")
  public List<FundResponse> get(
      @RequestParam("fundManager.name") Optional<String> fundManagerName,
      @RequestHeader(value = "Accept-Language", defaultValue = "et") String language) {

    List<FundResponse> funds =
        fundService.getFunds(fundManagerName, language == null ? "et" : language.toLowerCase());
    return funds;
  }
}
