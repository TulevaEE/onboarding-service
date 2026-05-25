package ee.tuleva.onboarding.statistics;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/statistics")
public class InvestorStatisticsController {

  private final InvestorStatisticsRepository investorStatisticsRepository;

  @Operation(summary = "Get total count of active investors across Tuleva index funds")
  @GetMapping("/investor-count")
  public InvestorCountResponse getInvestorCount() {
    return new InvestorCountResponse(investorStatisticsRepository.getActiveInvestorCount());
  }
}
