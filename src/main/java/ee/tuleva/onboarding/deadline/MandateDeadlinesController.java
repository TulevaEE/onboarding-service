package ee.tuleva.onboarding.deadline;

import static ee.tuleva.onboarding.deadline.MandateDeadlinesController.MANDATE_DEADLINES_URI;

import io.swagger.annotations.ApiOperation;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATE_DEADLINES_URI)
@RequiredArgsConstructor
public class MandateDeadlinesController {
  public static final String MANDATE_DEADLINES_URI = "/mandate-deadlines";

  private final Clock clock;
  private final PublicHolidays publicHolidays;

  @ApiOperation(value = "Get mandate deadlines")
  @GetMapping
  public MandateDeadlines get() {
    return new MandateDeadlines(clock, publicHolidays);
  }
}
