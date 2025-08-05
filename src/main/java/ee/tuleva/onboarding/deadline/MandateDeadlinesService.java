package ee.tuleva.onboarding.deadline;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MandateDeadlinesService {

  private final Clock estonianClock;
  private final PublicHolidays publicHolidays;

  public MandateDeadlines getDeadlines() {
    return new MandateDeadlines(estonianClock, publicHolidays, Instant.now(estonianClock));
  }

  public MandateDeadlines getDeadlines(Instant applicationDate) {
    return new MandateDeadlines(estonianClock, publicHolidays, applicationDate);
  }

  public LocalDate getCurrentPeriodStartDate() {
    return getDeadlines().getCurrentPeriodStartDate();
  }

  public LocalDate getPeriodStartDate(LocalDate date) {
    return getDeadlines(date.atStartOfDay(estonianClock.getZone()).toInstant())
        .getCurrentPeriodStartDate();
  }
}
