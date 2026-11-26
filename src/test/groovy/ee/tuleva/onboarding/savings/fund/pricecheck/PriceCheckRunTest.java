package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.UnitDealing;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PriceCheckRunTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.001");
  private static final BigDecimal NAV = new BigDecimal("1.1000");
  private static final LocalDate NAV_DATE = LocalDate.parse("2026-11-24");
  private static final BookingDays WEDNESDAY =
      new BookingDays(LocalDate.parse("2026-11-25"), LocalDate.parse("2026-11-26"));

  @Test
  void dealsAtTheirNavOrOffOnlyByRoundingRaiseNoAlert() {
    var run =
        new PriceCheckRun(
            WEDNESDAY,
            THRESHOLD,
            List.of(
                price(id(1), FUND_SUBSCRIPTION, "909.09090", "1000.00", NAV, NAV),
                price(id(2), REDEMPTION_REQUEST, "0.33333", "0.37", NAV, NAV)));

    assertThat(run.alert()).isEmpty();
    assertThat(run.roundingOnly())
        .extracting(DealingPrice::dealing)
        .extracting(UnitDealing::transactionId)
        .containsExactly(id(2));
  }

  @Test
  void namesEveryDealNotAtItsNavWithWhatIsWrongAndWhatToDo() {
    var run =
        new PriceCheckRun(
            WEDNESDAY,
            THRESHOLD,
            List.of(
                price(id(1), FUND_SUBSCRIPTION, "909.09090", "1000.00", NAV, NAV),
                price(id(2), REDEMPTION_REQUEST, "100.00000", "120.00", NAV, NAV),
                price(
                    id(3), FUND_SUBSCRIPTION, "917.43119", "1000.00", new BigDecimal("1.09"), NAV),
                price(id(4), FUND_SUBSCRIPTION, "909.09090", "1000.00", NAV, null),
                price(id(5), REDEMPTION_REQUEST, "100.00000", "110.00", null, NAV)));

    assertThat(run.alert())
        .contains(
            """
            ⚠️ TKF100 issue/redemption prices: 4 of 5 deals booked 2026-11-25 are not at their NAV
            • redemption 00000000-0000-0000-0000-000000000002: price 1.200000 vs NAV 1.1000 of 2026-11-24 (+9.09%)
            • subscription 00000000-0000-0000-0000-000000000003: booked at NAV 1.0900, the published NAV of 2026-11-24 is 1.1000
            • subscription 00000000-0000-0000-0000-000000000004: no published NAV of 2026-11-24 to compare with
            • redemption 00000000-0000-0000-0000-000000000005: booking lacks its units, cash, NAV or NAV date
            Next: find the cause; a holder who got a wrong price is corrected and compensated as for a NAV error""");
  }

  @Test
  void namesTheBookingDaysOfAWindowThatSpansAWeekend() {
    var mondayRun =
        new PriceCheckRun(
            new BookingDays(LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-23")),
            THRESHOLD,
            List.of(price(id(1), REDEMPTION_REQUEST, "100.00000", "90.00", NAV, NAV)));

    assertThat(mondayRun.alert())
        .hasValueSatisfying(
            alert ->
                assertThat(alert.lines().toList())
                    .startsWith(
                        "⚠️ TKF100 issue/redemption prices: 1 of 1 deals booked 2026-11-20–2026-11-22 are not at their NAV",
                        "• redemption 00000000-0000-0000-0000-000000000001: price 0.900000 vs NAV 1.1000 of 2026-11-24 (-18.18%)"));
  }

  private static UUID id(int n) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(n));
  }

  private static DealingPrice price(
      UUID id,
      TransactionType type,
      String units,
      String cash,
      @Nullable BigDecimal bookedNav,
      @Nullable BigDecimal publishedNav) {
    return new DealingPrice(
        new UnitDealing(
            id,
            type,
            Instant.parse("2026-11-25T14:05:00Z"),
            new BigDecimal(units),
            new BigDecimal(cash),
            bookedNav,
            NAV_DATE),
        publishedNav);
  }
}
