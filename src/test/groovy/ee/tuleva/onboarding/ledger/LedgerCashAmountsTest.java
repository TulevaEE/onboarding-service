package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_CANCELLED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REDEMPTION_REQUEST;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SavingsFundLedgerStackConfiguration.class)
class LedgerCashAmountsTest {

  private static final PartyRef PARTY = new PartyRef(PERSON, "38888888888");
  private static final LocalDate NAV_DATE = LocalDate.parse("2026-09-22");
  private static final BigDecimal NAV = new BigDecimal("1.25000");
  private static final Instant PREVIOUS_BRIEF = Instant.parse("2026-09-22T13:10:00Z");
  private static final Instant BRIEF = Instant.parse("2026-09-23T13:10:00Z");

  @Autowired LedgerCashAmounts ledgerCashAmounts;
  @Autowired SavingsFundLedger savingsFundLedger;

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void cashAmountOf_aPricedRedemptionIsTheCashItWasPricedAt() {
    var redemptionRequestId = randomUUID();
    subscribe("1000.00", "800.00000", randomUUID());
    savingsFundLedger.reserveFundUnitsForRedemption(
        PARTY, new BigDecimal("240.00000"), redemptionRequestId);
    savingsFundLedger.redeemFundUnitsFromReserved(
        PARTY,
        new BigDecimal("240.00000"),
        new BigDecimal("300.00"),
        NAV,
        NAV_DATE,
        redemptionRequestId);

    assertThat(ledgerCashAmounts.cashAmountOf(redemptionRequestId, REDEMPTION_REQUEST))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("300.00"));
  }

  @Test
  void cashAmountOf_severalReferencesSumsThePricingOfThoseThatHaveOne() {
    var first = priceRedemption("300.00", "240.00000");
    var second = priceRedemption("125.50", "100.40000");

    assertThat(
            ledgerCashAmounts.cashAmountOf(
                List.of(first, second, randomUUID()), REDEMPTION_REQUEST))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("425.50"));
  }

  @Test
  void cashAmountOf_severalReferencesIsEmptyWhenNoneHasOne() {
    assertThat(ledgerCashAmounts.cashAmountOf(List.of(randomUUID()), REDEMPTION_REQUEST)).isEmpty();
    assertThat(ledgerCashAmounts.cashAmountOf(List.of(), REDEMPTION_REQUEST)).isEmpty();
  }

  @Test
  void cashAmountOf_isEmptyWhenTheLedgerHasNoSuchTransaction() {
    assertThat(ledgerCashAmounts.cashAmountOf(randomUUID(), REDEMPTION_REQUEST)).isEmpty();
  }

  @Test
  void cashAmountOf_aCancelledPaymentIsTheAmountReturned() {
    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(PARTY, new BigDecimal("150.00"), paymentId);
    savingsFundLedger.recordPaymentCancelled(PARTY, new BigDecimal("150.00"), paymentId);

    assertThat(ledgerCashAmounts.cashAmountOf(paymentId, PAYMENT_CANCELLED))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("150.00"));
  }

  @Test
  void cashAmountOf_aBouncedBackPaymentIsTheAmountReturned() {
    var paymentId = randomUUID();
    savingsFundLedger.recordUnattributedPayment(new BigDecimal("75.50"), paymentId);
    savingsFundLedger.bounceBackUnattributedPayment(new BigDecimal("75.50"), paymentId);

    assertThat(ledgerCashAmounts.cashAmountOf(paymentId, PAYMENT_BOUNCE_BACK))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("75.50"));
  }

  @Test
  void cashAmountBetween_sumsTheSubscriptionsIssuedAfterTheFirstInstantUpToTheSecond() {
    issueAt(PREVIOUS_BRIEF, "1.00");
    issueAt(PREVIOUS_BRIEF.plusSeconds(60), "100.00");
    issueAt(BRIEF.minusSeconds(600), "250.00");
    issueAt(BRIEF, "50.00");
    issueAt(BRIEF.plusSeconds(1), "7.00");

    assertThat(ledgerCashAmounts.cashAmountBetween(FUND_SUBSCRIPTION, PREVIOUS_BRIEF, BRIEF))
        .isEqualByComparingTo("400.00");
  }

  @Test
  void cashAmountBetween_isZeroWhenNothingWasIssued() {
    assertThat(ledgerCashAmounts.cashAmountBetween(FUND_SUBSCRIPTION, PREVIOUS_BRIEF, BRIEF))
        .isEqualByComparingTo("0");
  }

  private UUID priceRedemption(String cash, String units) {
    var redemptionRequestId = randomUUID();
    subscribe(cash, units, randomUUID());
    savingsFundLedger.reserveFundUnitsForRedemption(
        PARTY, new BigDecimal(units), redemptionRequestId);
    savingsFundLedger.redeemFundUnitsFromReserved(
        PARTY, new BigDecimal(units), new BigDecimal(cash), NAV, NAV_DATE, redemptionRequestId);
    return redemptionRequestId;
  }

  private void issueAt(Instant issuedAt, String cash) {
    ClockHolder.setClock(Clock.fixed(issuedAt, UTC));
    subscribe(cash, new BigDecimal(cash).divide(NAV).setScale(5).toPlainString(), randomUUID());
  }

  private void subscribe(String cash, String units, UUID paymentId) {
    var cashAmount = new BigDecimal(cash);
    savingsFundLedger.recordPaymentReceived(PARTY, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(PARTY, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        PARTY, cashAmount, new BigDecimal(units), NAV, NAV_DATE, paymentId);
  }
}
