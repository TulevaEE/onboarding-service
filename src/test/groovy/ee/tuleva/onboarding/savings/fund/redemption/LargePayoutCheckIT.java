package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.LARGE_PAYOUT;
import static java.math.RoundingMode.HALF_UP;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerStackConfiguration;
import ee.tuleva.onboarding.ledger.SavingsFundUnits;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({SavingsFundLedgerStackConfiguration.class, SavingsFundUnits.class, LargePayoutCheck.class})
class LargePayoutCheckIT {

  private static final PartyId PERSON_PARTY = new PartyId(PERSON, "38888888888");
  private static final PartyId COMPANY_PARTY = new PartyId(LEGAL_ENTITY, "10000000");
  private static final Instant REQUESTED_AT = Instant.parse("2026-11-20T10:00:00Z");
  private static final Instant A_MONTH_BEFORE = REQUESTED_AT.minus(30, DAYS);
  private static final Instant A_WEEK_BEFORE = REQUESTED_AT.minus(7, DAYS);

  @Autowired LargePayoutCheck largePayoutCheck;
  @Autowired SavingsFundLedger savingsFundLedger;

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aPayoutOfMoreThanTenThousandEurosIsLargeWhateverShareOfTheHoldingItIs() {
    subscribe(PERSON_PARTY, "200000.00000", A_MONTH_BEFORE);

    var request = request(PERSON_PARTY, "10000.01", "10000.01000");

    assertThat(largePayoutCheck.raisedBy(request)).contains(LARGE_PAYOUT);
  }

  @Test
  void aPayoutOfExactlyTenThousandEurosIsNotLargeByItsAmount() {
    subscribe(PERSON_PARTY, "200000.00000", A_MONTH_BEFORE);

    var request = request(PERSON_PARTY, "10000.00", "10000.00000");

    assertThat(largePayoutCheck.raisedBy(request)).isEmpty();
  }

  @Test
  void redeemingMoreThanEightyPercentOfTheUnitsHeldBeforeTheRequestIsLarge() {
    subscribe(PERSON_PARTY, "100.00000", A_MONTH_BEFORE);
    reserve(PERSON_PARTY, "80.00001", REQUESTED_AT);

    var request = request(PERSON_PARTY, "80.00", "80.00001");

    assertThat(largePayoutCheck.raisedBy(request)).contains(LARGE_PAYOUT);
  }

  @Test
  void redeemingExactlyEightyPercentOfTheUnitsHeldBeforeTheRequestIsNotLarge() {
    subscribe(PERSON_PARTY, "100.00000", A_MONTH_BEFORE);
    reserve(PERSON_PARTY, "80.00000", REQUESTED_AT);

    var request = request(PERSON_PARTY, "80.00", "80.00000");

    assertThat(largePayoutCheck.raisedBy(request)).isEmpty();
  }

  @Test
  void unitsReservedForAnEarlierRedemptionStillCountAsHeld() {
    subscribe(PERSON_PARTY, "100.00000", A_MONTH_BEFORE);
    reserve(PERSON_PARTY, "50.00000", A_MONTH_BEFORE.plus(1, DAYS));
    reserve(PERSON_PARTY, "45.00000", REQUESTED_AT);

    var request = request(PERSON_PARTY, "45.00", "45.00000");

    assertThat(largePayoutCheck.raisedBy(request)).isEmpty();
  }

  @Test
  void unitsAlreadyRedeemedForAnEarlierRequestLeaveLessHeldToMeasureTheNextAgainst() {
    subscribe(PERSON_PARTY, "100.00000", A_MONTH_BEFORE);
    reserve(PERSON_PARTY, "50.00000", A_WEEK_BEFORE);
    redeem(PERSON_PARTY, "50.00000", A_WEEK_BEFORE.plus(1, DAYS));
    reserve(PERSON_PARTY, "45.00000", REQUESTED_AT);

    var request = request(PERSON_PARTY, "45.00", "45.00000");

    assertThat(largePayoutCheck.raisedBy(request)).contains(LARGE_PAYOUT);
  }

  @Test
  void unitsSubscribedAfterTheRequestDoNotMakeItsShareSmaller() {
    subscribe(PERSON_PARTY, "100.00000", A_MONTH_BEFORE);
    reserve(PERSON_PARTY, "90.00000", REQUESTED_AT);
    subscribe(PERSON_PARTY, "900.00000", REQUESTED_AT.plus(1, DAYS));

    var request = request(PERSON_PARTY, "90.00", "90.00000");

    assertThat(largePayoutCheck.raisedBy(request)).contains(LARGE_PAYOUT);
  }

  @Test
  void aCompanysPayoutIsMeasuredAgainstTheCompanysOwnHolding() {
    subscribe(COMPANY_PARTY, "100.00000", A_MONTH_BEFORE);
    subscribe(PERSON_PARTY, "100000.00000", A_MONTH_BEFORE);
    reserve(COMPANY_PARTY, "90.00000", REQUESTED_AT);

    var request = request(COMPANY_PARTY, "90.00", "90.00000");

    assertThat(largePayoutCheck.raisedBy(request)).contains(LARGE_PAYOUT);
  }

  @Test
  void aRequestOfAPartyWithoutUnitAccountsCannotBeMeasured() {
    var request = request(PERSON_PARTY, "90.00", "90.00000");

    assertThatThrownBy(() -> largePayoutCheck.raisedBy(request))
        .isInstanceOf(IllegalStateException.class);
  }

  private void subscribe(PartyId party, String units, Instant at) {
    ClockHolder.setClock(Clock.fixed(at, UTC));
    var paymentId = UUID.randomUUID();
    var partyRef = LedgerRefs.from(party);
    var cash = new BigDecimal(units).setScale(2, HALF_UP);
    savingsFundLedger.recordPaymentReceived(partyRef, cash, paymentId);
    savingsFundLedger.reservePaymentForSubscription(partyRef, cash, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        partyRef, cash, new BigDecimal(units), BigDecimal.ONE, tallinnDateOf(at), paymentId);
  }

  private void reserve(PartyId party, String units, Instant at) {
    ClockHolder.setClock(Clock.fixed(at, UTC));
    savingsFundLedger.reserveFundUnitsForRedemption(
        LedgerRefs.from(party), new BigDecimal(units), UUID.randomUUID());
  }

  private void redeem(PartyId party, String units, Instant at) {
    ClockHolder.setClock(Clock.fixed(at, UTC));
    savingsFundLedger.redeemFundUnitsFromReserved(
        LedgerRefs.from(party),
        new BigDecimal(units),
        new BigDecimal(units).setScale(2, HALF_UP),
        BigDecimal.ONE,
        tallinnDateOf(at),
        UUID.randomUUID());
  }

  private static LocalDate tallinnDateOf(Instant at) {
    return LocalDate.ofInstant(at, ZoneId.of("Europe/Tallinn"));
  }

  private static RedemptionRequest request(PartyId party, String amount, String units) {
    return redemptionRequestFixture()
        .id(UUID.randomUUID())
        .partyType(party.type())
        .partyCode(party.code())
        .requestedAmount(new BigDecimal(amount))
        .fundUnits(new BigDecimal(units))
        .requestedAt(REQUESTED_AT)
        .build();
  }
}
