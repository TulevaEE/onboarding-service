package ee.tuleva.onboarding.statistics;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/statistics")
public class InvestorStatisticsController {

  private final InvestorStatisticsService investorStatisticsService;

  @Operation(summary = "Get total count of active investors across Tuleva index funds")
  @GetMapping("/investor-count")
  public ResponseEntity<InvestorCountResponse> getInvestorCount() {
    InvestorCountResponse response =
        new InvestorCountResponse(investorStatisticsService.getActiveInvestorCount());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(response);
  }
}
