package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingFundDeadlinesService {
  private final PublicHolidays publicHolidays;
  private final Clock estonianClock;

  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0);

  private LocalDate firstWorkingDayOnOrAfter(LocalDate date) {
    return publicHolidays.nextWorkingDay(date.minusDays(1));
  }

  public Instant getCancellationDeadline(SavingFundPayment payment) {
    return getCancellationDeadline(payment.getReceivedBefore());
  }

  public Instant getCancellationDeadline(@Nullable Instant receivedAt) {
    ZoneId timeZone = estonianClock.getZone();
    Instant now = Instant.now(estonianClock);
    LocalDate today = now.atZone(timeZone).toLocalDate();

    if (receivedAt != null) {
      ZonedDateTime receivedZdt = receivedAt.atZone(timeZone);
      LocalDate receivedDate = receivedZdt.toLocalDate();
      LocalTime receivedTime = receivedZdt.toLocalTime();

      if (receivedTime.isBefore(CUTOFF_TIME)) {
        // Received before cutoff → same working day 16:00
        LocalDate workDay = firstWorkingDayOnOrAfter(receivedDate);
        return workDay.atTime(CUTOFF_TIME).atZone(timeZone).toInstant();
      } else {
        // Received at/after cutoff → next working day 16:00
        LocalDate nextWork = publicHolidays.nextWorkingDay(receivedDate);
        return nextWork.atTime(CUTOFF_TIME).atZone(timeZone).toInstant();
      }
    } else {
      // Not received yet → assume cancellable until today 16:00
      LocalDate workDay = firstWorkingDayOnOrAfter(today);
      return workDay.atTime(CUTOFF_TIME).atZone(timeZone).toInstant();
    }
  }

  public Instant getFulfillmentDeadline(SavingFundPayment payment) {
    return getFulfillmentDeadline(payment.getReceivedBefore());
  }

  public Instant getFulfillmentDeadline(@Nullable Instant receivedAt) {
    ZoneId timeZone = estonianClock.getZone();
    Instant now = Instant.now(estonianClock);
    LocalDate today = now.atZone(timeZone).toLocalDate();

    LocalDate baseDate;
    if (receivedAt != null) {
      baseDate = receivedAt.atZone(timeZone).toLocalDate();
    } else {
      // Not received yet → assume it will arrive today after cutoff
      baseDate = today;
    }

    // First working day after received date
    LocalDate nextWork = publicHolidays.nextWorkingDay(baseDate);
    // Second working day after received date
    LocalDate fulfillmentDay = publicHolidays.nextWorkingDay(nextWork);

    return fulfillmentDay.atTime(CUTOFF_TIME).atZone(timeZone).toInstant();
  }
}
