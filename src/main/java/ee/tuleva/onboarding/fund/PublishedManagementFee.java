package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishedManagementFee {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final ManagementFeeRates managementFeeRates;
  private final Clock clock;

  public <T extends ApiFundResponse> T applyTo(T response) {
    TulevaFund.findByIsin(response.getIsin())
        .flatMap(this::rateInForceToday)
        .map(BigDecimal::stripTrailingZeros)
        .ifPresent(response::setManagementFeeRate);
    return response;
  }

  private Optional<BigDecimal> rateInForceToday(TulevaFund fund) {
    return managementFeeRates.rateInForceOn(fund, LocalDate.now(clock.withZone(ESTONIAN_ZONE)));
  }
}
