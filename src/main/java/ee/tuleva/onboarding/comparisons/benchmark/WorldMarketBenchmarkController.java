package ee.tuleva.onboarding.comparisons.benchmark;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
class WorldMarketBenchmarkController {

  private final WorldMarketBenchmarkService worldMarketBenchmarkService;

  @Operation(summary = "Get annualized world market benchmark returns for 1-10 year windows")
  @GetMapping("/benchmarks/world-market/returns")
  ResponseEntity<WorldMarketReturnsResponse> getReturns() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(new WorldMarketReturnsResponse(worldMarketBenchmarkService.getReturns()));
  }
}
