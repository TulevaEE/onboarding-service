package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SecondPillarRejoinWindows {

  private final Clock estonianClock;
  private final MandateDeadlinesService mandateDeadlinesService;

  SecondPillarRejoinWindow current() {
    LocalDate deadline = mandateDeadlinesService.getDeadlines().getSecondPillarRejoinDeadlineDate();
    boolean inTheFilingDeadlineMonth =
        YearMonth.from(LocalDate.now(estonianClock)).equals(YearMonth.from(deadline));
    return new SecondPillarRejoinWindow(deadline, inTheFilingDeadlineMonth);
  }
}
