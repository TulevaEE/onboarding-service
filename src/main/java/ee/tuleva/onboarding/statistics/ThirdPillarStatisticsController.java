package ee.tuleva.onboarding.statistics;

import io.swagger.v3.oas.annotations.Operation;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/statistics")
@Slf4j
@RequiredArgsConstructor
public class ThirdPillarStatisticsController {

  private final ThirdPillarStatisticsRepository repository;

  @PostMapping
  @Operation(summary = "Post third pillar statistics")
  public ThirdPillarStatistics postStats(@Valid @RequestBody ThirdPillarStatistics statistics) {
    return repository.save(statistics);
  }
}
