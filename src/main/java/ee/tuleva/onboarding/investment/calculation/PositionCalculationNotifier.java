package ee.tuleva.onboarding.investment.calculation;

import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionCalculationNotifier {

  private final PositionCalculationNotificationSender notificationSender;

  public void notifyPriceDiscrepancy(
      TulevaFund fund,
      String isin,
      LocalDate date,
      BigDecimal eodhdPrice,
      BigDecimal yahooPrice,
      BigDecimal discrepancyPercent) {

    log.error(
        "Position calculation price discrepancy: fund={}, isin={}, date={}, "
            + "eodhdPrice={}, yahooPrice={}, discrepancyPercent={}%",
        fund, isin, date, eodhdPrice, yahooPrice, discrepancyPercent);

    notificationSender.send(
        String.format(
            "Price discrepancy: %s (%s) on %s - EODHD: %s, Yahoo: %s (%s%% diff)",
            fund, isin, date, eodhdPrice, yahooPrice, discrepancyPercent));
  }

  public void notifyYahooMissing(
      TulevaFund fund, String isin, LocalDate date, BigDecimal eodhdPrice) {

    log.error(
        "Position calculation Yahoo price missing: fund={}, isin={}, date={}, usingEodhdPrice={}",
        fund,
        isin,
        date,
        eodhdPrice);

    notificationSender.send(
        String.format(
            "Yahoo price missing: %s (%s) on %s - using EODHD: %s", fund, isin, date, eodhdPrice));
  }

  public void notifyNoPriceData(TulevaFund fund, String isin, LocalDate date) {

    log.error(
        "Position calculation blocked - no price data: fund={}, isin={}, date={}",
        fund,
        isin,
        date);

    notificationSender.send(
        String.format("BLOCKED - no price data: %s (%s) on %s", fund, isin, date));
  }

  public void notifyEodhdMissing(
      TulevaFund fund, String isin, LocalDate date, BigDecimal yahooPrice) {

    log.error(
        "Position calculation EODHD price missing: fund={}, isin={}, date={}, usingYahooPrice={}",
        fund,
        isin,
        date,
        yahooPrice);

    notificationSender.send(
        String.format(
            "EODHD price missing: %s (%s) on %s - using Yahoo: %s", fund, isin, date, yahooPrice));
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
