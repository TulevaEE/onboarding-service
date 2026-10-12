package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublishedManagementFee {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final ManagementFeeRates managementFeeRates;
  private final Clock clock;

  public <T extends ApiFundResponse> T applyTo(T response) {
    TulevaFund.findByIsin(response.getIsin())
        .ifPresent(fund -> applyRateInForceToday(fund, response));
    return response;
  }

  private void applyRateInForceToday(TulevaFund fund, ApiFundResponse response) {
    var today = LocalDate.now(clock.withZone(ESTONIAN_ZONE));
    managementFeeRates
        .rateInForceOn(fund, today)
        .map(BigDecimal::stripTrailingZeros)
        .ifPresentOrElse(
            response::setManagementFeeRate,
            () ->
                log.error(
                    "No management fee rate in force, showing the fund table rate: fund={}, date={}",
                    fund,
                    today));
  }
}
