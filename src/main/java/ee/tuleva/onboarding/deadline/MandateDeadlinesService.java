package ee.tuleva.onboarding.deadline;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MandateDeadlinesService {

  private final Clock estonianClock;
  private final PublicHolidays publicHolidays;

  public MandateDeadlines getDeadlines() {
    return new MandateDeadlines(estonianClock, publicHolidays);
  }
}
