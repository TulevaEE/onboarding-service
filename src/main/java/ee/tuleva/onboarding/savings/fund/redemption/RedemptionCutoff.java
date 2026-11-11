package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.TradeDay.SISEKORD_16_P_1_1_3_ORDER_CUTOFF;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class RedemptionCutoff {

  static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  static Instant cutoffInstant(LocalDate date) {
    return ZonedDateTime.of(date, SISEKORD_16_P_1_1_3_ORDER_CUTOFF, TALLINN).toInstant();
  }

  private RedemptionCutoff() {}
}
