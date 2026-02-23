package ee.tuleva.onboarding.investment.calculation;

import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionCalculationNotifier {

  private final PositionCalculationNotificationSender notificationSender;

  public void notifyNoPriceData(TulevaFund fund, String isin, LocalDate date) {
    log.error(
        "Position calculation blocked - no price data: fund={}, isin={}, date={}",
        fund,
        isin,
        date);

    notificationSender.send(
        String.format("BLOCKED - no price data: %s (%s) on %s", fund, isin, date));
  }

  public void notifyStalePrice(
      TulevaFund fund, String isin, LocalDate requestedDate, LocalDate priceDate) {
    long daysOld = DAYS.between(priceDate, requestedDate);

    log.warn(
        "Position calculation using stale price: fund={}, isin={}, requestedDate={}, "
            + "priceDate={}, daysOld={}",
        fund,
        isin,
        requestedDate,
        priceDate,
        daysOld);

    notificationSender.send(
        String.format(
            "Stale price: %s (%s) on %s - using price from %s (%d days old)",
            fund, isin, requestedDate, priceDate, daysOld));
  }
}
