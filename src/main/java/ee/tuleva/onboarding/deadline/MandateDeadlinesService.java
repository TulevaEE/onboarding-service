package ee.tuleva.onboarding.deadline;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MandateDeadlinesService {

  private final Clock clock;
  private final PublicHolidays publicHolidays;

  public MandateDeadlines getDeadlines() {
    return new MandateDeadlines(clock, publicHolidays);
  }
}
