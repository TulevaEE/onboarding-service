package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.INCOMPLETE_BOOKING;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.NAV_DIFFERS_FROM_PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.NAV_NOT_PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.PRICED_AT_THE_NAV;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.PRICE_DEVIATES;
import static ee.tuleva.onboarding.savings.fund.pricecheck.DealingPriceVerdict.ROUNDING_ONLY;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.UnitDealing;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DealingPriceTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("0.001");
  private static final BigDecimal NAV = new BigDecimal("1.1000");
  private static final LocalDate NAV_DATE = LocalDate.parse("2026-11-24");

  @Test
  void aSubscriptionWhoseUnitsAreTheCashOverTheNavIsPricedAtTheNav() {
    var price =
        new DealingPrice(dealing(FUND_SUBSCRIPTION, "909.09090", "1000.00", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICED_AT_THE_NAV);
  }

  @Test
  void aRedemptionPaidTheUnitsTimesTheNavIsPricedAtTheNav() {
    var price =
        new DealingPrice(dealing(REDEMPTION_REQUEST, "100.00000", "110.00", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICED_AT_THE_NAV);
  }

  @Test
  void aDeviationJustUnderTheThresholdIsPricedAtTheNav() {
    var price =
        new DealingPrice(dealing(REDEMPTION_REQUEST, "100.00000", "110.10", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICED_AT_THE_NAV);
  }

  @Test
  void aRedemptionPaidMoreThanTheThresholdAboveTheNavDeviates() {
    var price =
        new DealingPrice(dealing(REDEMPTION_REQUEST, "100.00000", "110.12", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICE_DEVIATES);
  }

  @Test
  void aSubscriptionGivenUnitsAtAnotherPriceDeviates() {
    var price =
        new DealingPrice(dealing(FUND_SUBSCRIPTION, "900.00000", "1000.00", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICE_DEVIATES);
  }

  @Test
  void aTinyRedemptionWhosePayoutWasRoundedToTheCentIsOnlyRounding() {
    var price =
        new DealingPrice(dealing(REDEMPTION_REQUEST, "0.33333", "0.37", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(ROUNDING_ONLY);
  }

  @Test
  void aRedemptionRoundedTheWrongWayAtAHalfCentDeviates() {
    var price =
        new DealingPrice(dealing(REDEMPTION_REQUEST, "4.45000", "4.89", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICE_DEVIATES);
  }

  @Test
  void aSubscriptionWhoseUnitsMissTheirFiveDecimalRoundingDeviatesWhenOverTheThreshold() {
    var price = new DealingPrice(dealing(FUND_SUBSCRIPTION, "0.00911", "0.01", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICE_DEVIATES);
  }

  @Test
  void aDealingBookedAtANavOtherThanThePublishedOneOfItsDateIsReported() {
    var price =
        new DealingPrice(
            dealing(FUND_SUBSCRIPTION, "917.43119", "1000.00", new BigDecimal("1.0900"), NAV_DATE),
            NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(NAV_DIFFERS_FROM_PUBLISHED);
  }

  @Test
  void theBookedNavMatchesThePublishedOneWhateverTheirTrailingZeros() {
    var price =
        new DealingPrice(
            dealing(FUND_SUBSCRIPTION, "909.09090", "1000.00", new BigDecimal("1.1"), NAV_DATE),
            NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(PRICED_AT_THE_NAV);
  }

  @Test
  void aDealingWhoseNavDateHasNoPublishedNavIsReported() {
    var price =
        new DealingPrice(dealing(FUND_SUBSCRIPTION, "909.09090", "1000.00", NAV, NAV_DATE), null);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(NAV_NOT_PUBLISHED);
  }

  @Test
  void aDealingBookedWithoutItsNavIsAnIncompleteBooking() {
    var price =
        new DealingPrice(dealing(FUND_SUBSCRIPTION, "909.09090", "1000.00", null, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(INCOMPLETE_BOOKING);
  }

  @Test
  void aDealingBookedWithoutItsNavDateIsAnIncompleteBooking() {
    var price =
        new DealingPrice(dealing(FUND_SUBSCRIPTION, "909.09090", "1000.00", NAV, null), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(INCOMPLETE_BOOKING);
  }

  @Test
  void aDealingWithoutUnitsIsAnIncompleteBooking() {
    var price = new DealingPrice(dealing(FUND_SUBSCRIPTION, "0", "1000.00", NAV, NAV_DATE), NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(INCOMPLETE_BOOKING);
  }

  @Test
  void aDealingWithoutCashIsAnIncompleteBooking() {
    var price =
        new DealingPrice(
            new UnitDealing(
                UUID.randomUUID(),
                REDEMPTION_REQUEST,
                Instant.parse("2026-11-25T14:05:00Z"),
                new BigDecimal("100.00000"),
                null,
                NAV,
                NAV_DATE),
            NAV);

    assertThat(price.verdict(THRESHOLD)).isEqualTo(INCOMPLETE_BOOKING);
  }

  @Test
  void theDeviationIsTheRealisedPriceOverTheNav() {
    var price =
        new DealingPrice(dealing(REDEMPTION_REQUEST, "100.00000", "110.55", NAV, NAV_DATE), NAV);

    assertThat(price.realisedPrice()).isEqualByComparingTo("1.1055");
    assertThat(price.deviation()).isEqualByComparingTo("0.005");
  }

  private static UnitDealing dealing(
      TransactionType type,
      String units,
      String cash,
      @Nullable BigDecimal nav,
      @Nullable LocalDate navDate) {
    return new UnitDealing(
        UUID.randomUUID(),
        type,
        Instant.parse("2026-11-25T14:05:00Z"),
        new BigDecimal(units),
        new BigDecimal(cash),
        nav,
        navDate);
  }
}
