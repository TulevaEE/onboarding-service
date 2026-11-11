package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.savings.TradeDay.SISEKORD_16_P_1_1_3_ORDER_CUTOFF;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingFundDeadlinesService {
  private final PublicHolidays publicHolidays;
  private final Clock estonianClock;

  private static final LocalTime SCREENING_RETRY_CUTOFF_TIME = LocalTime.of(15, 0);
  private static final Duration MINIMUM_SCREENING_RETRY_WINDOW = Duration.ofMinutes(10);

  public Instant getCancellationDeadline(RedemptionRequest redemptionRequest) {
    return getCancellationDeadlineFrom(redemptionRequest.getRequestedAt());
  }

  public Instant getCancellationDeadline(SavingFundPayment payment) {
    if (payment.getReceivedBefore() != null) {
      return getCancellationDeadlineFrom(payment.getReceivedBefore());
    }
    return getCancellationDeadlineFrom(payment.getCreatedAt());
  }

  public Instant getScreeningRetryDeadline(RedemptionRequest redemptionRequest) {
    Instant requestedAt = redemptionRequest.getRequestedAt();
    Instant dealingDayCutoff =
        dealingDay(requestedAt)
            .atTime(SCREENING_RETRY_CUTOFF_TIME)
            .atZone(estonianClock.getZone())
            .toInstant();
    Instant minimumRetryWindow = requestedAt.plus(MINIMUM_SCREENING_RETRY_WINDOW);
    return dealingDayCutoff.isAfter(minimumRetryWindow) ? dealingDayCutoff : minimumRetryWindow;
  }

  private Instant getCancellationDeadlineFrom(Instant eventInstant) {
    return dealingDay(eventInstant)
        .atTime(SISEKORD_16_P_1_1_3_ORDER_CUTOFF)
        .atZone(estonianClock.getZone())
        .toInstant();
  }

  private LocalDate dealingDay(Instant eventInstant) {
    ZonedDateTime zdt = eventInstant.atZone(estonianClock.getZone());
    return TradeDay.dealingDay(zdt.toLocalDate(), zdt.toLocalTime(), publicHolidays);
  }

  public Instant getFulfillmentDeadline(SavingFundPayment payment) {
    Instant cancellationDeadline = getCancellationDeadline(payment);
    return fulfillmentDeadlineFrom(cancellationDeadline);
  }

  public Instant getFulfillmentDeadline(RedemptionRequest redemptionRequest) {
    Instant cancellationDeadline = getCancellationDeadline(redemptionRequest);
    return fulfillmentDeadlineFrom(cancellationDeadline);
  }

  private Instant fulfillmentDeadlineFrom(Instant instant) {
    ZoneId timeZone = estonianClock.getZone();
    ZonedDateTime zdt = instant.atZone(timeZone);
    LocalDate date = zdt.toLocalDate();

    LocalDate nextWorkingDay = publicHolidays.addWorkingDays(date, 1);
    return nextWorkingDay.atTime(SISEKORD_16_P_1_1_3_ORDER_CUTOFF).atZone(timeZone).toInstant();
  }
}
