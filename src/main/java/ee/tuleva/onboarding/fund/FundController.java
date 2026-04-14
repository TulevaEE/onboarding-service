package ee.tuleva.onboarding.fund;

import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundController {

  private final FundService fundService;

  @Operation(summary = "Get info about available funds")
  @GetMapping("/funds")
  public List<ExtendedApiFundResponse> get(
      @RequestParam("fundManager.name") Optional<String> fundManagerName) {
    return fundService.getFunds(fundManagerName);
  }

  @Operation(summary = "Get NAV history for a fund")
  @GetMapping("/funds/{isin}/nav")
  public List<NavValueResponse> getNavHistory(
      @PathVariable String isin,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate) {
    return fundService.getNavHistory(isin, startDate, endDate);
  }
}
