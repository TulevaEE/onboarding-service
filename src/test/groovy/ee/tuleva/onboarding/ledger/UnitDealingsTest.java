package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({SavingsFundLedgerStackConfiguration.class, UnitDealings.class})
class UnitDealingsTest {

  private static final Instant WINDOW_START = Instant.parse("2026-11-24T22:00:00Z");
  private static final Instant WINDOW_END = Instant.parse("2026-11-25T22:00:00Z");
  private static final LocalDate NAV_DATE = LocalDate.parse("2026-11-24");

  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired UnitDealings unitDealings;

  PartyRef party = new PartyRef(PERSON, "38888888888");

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void readsTheUnitsCashAndNavOfEverySubscriptionAndRedemptionBookedInTheWindow() {
    bookAt("2026-11-25T14:05:00Z");
    UUID paymentId =
        subscribe(new BigDecimal("1000.00"), new BigDecimal("909.09090"), new BigDecimal("1.1000"));
    UUID redemptionId =
        redeem(new BigDecimal("100.00000"), new BigDecimal("110.00"), new BigDecimal("1.1000"));

    var dealings = unitDealings.bookedBetween(WINDOW_START, WINDOW_END);

    assertThat(dealings)
        .usingRecursiveFieldByFieldElementComparator(
            RecursiveComparisonConfiguration.builder()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .build())
        .containsExactlyInAnyOrder(
            new UnitDealing(
                paymentId,
                FUND_SUBSCRIPTION,
                Instant.parse("2026-11-25T14:05:00Z"),
                new BigDecimal("909.09090"),
                new BigDecimal("1000.00"),
                new BigDecimal("1.1000"),
                NAV_DATE),
            new UnitDealing(
                redemptionId,
                REDEMPTION_REQUEST,
                Instant.parse("2026-11-25T14:05:00Z"),
                new BigDecimal("100.00000"),
                new BigDecimal("110.00"),
                new BigDecimal("1.1000"),
                NAV_DATE));
  }

  @Test
  void leavesOutDealingsBookedBeforeTheWindowAndAtItsEnd() {
    bookAt("2026-11-24T21:59:59Z");
    subscribe(new BigDecimal("1000.00"), new BigDecimal("909.09090"), new BigDecimal("1.1000"));
    bookAt("2026-11-25T22:00:00Z");
    subscribe(new BigDecimal("500.00"), new BigDecimal("454.54545"), new BigDecimal("1.1000"));

    assertThat(unitDealings.bookedBetween(WINDOW_START, WINDOW_END)).isEmpty();
  }

  @Test
  void leavesOutBookingsThatAreNotSubscriptionsOrRedemptions() {
    bookAt("2026-11-25T14:05:00Z");
    savingsFundLedger.recordPaymentReceived(party, new BigDecimal("1000.00"), randomUUID());

    assertThat(unitDealings.bookedBetween(WINDOW_START, WINDOW_END)).isEmpty();
  }

  private void bookAt(String instant) {
    ClockHolder.setClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  private UUID subscribe(BigDecimal cash, BigDecimal units, BigDecimal nav) {
    UUID paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(party, cash, paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, cash, paymentId);
    return savingsFundLedger
        .issueFundUnitsFromReserved(party, cash, units, nav, NAV_DATE, paymentId)
        .getId();
  }

  private UUID redeem(BigDecimal units, BigDecimal cash, BigDecimal nav) {
    UUID requestId = randomUUID();
    savingsFundLedger.reserveFundUnitsForRedemption(party, units, requestId);
    return savingsFundLedger
        .redeemFundUnitsFromReserved(party, units, cash, nav, NAV_DATE, requestId)
        .getId();
  }
}
