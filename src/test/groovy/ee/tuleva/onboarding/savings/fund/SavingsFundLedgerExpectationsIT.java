package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.REDEEMED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerStackConfiguration;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({SavingsFundLedgerStackConfiguration.class, SavingsFundLedgerExpectations.class})
class SavingsFundLedgerExpectationsIT {

  private static final PartyRef PARTY = new PartyRef(PERSON, "38888888888");
  private static final LocalDate NAV_DATE = LocalDate.parse("2026-09-22");
  private static final BigDecimal NAV = new BigDecimal("1.25000");
  private static final UUID BATCH = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_BATCH = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final Instant PREVIOUS_BRIEF = Instant.parse("2026-09-22T13:10:00Z");
  private static final Instant BRIEF = Instant.parse("2026-09-23T13:10:00Z");

  @Autowired SavingsFundLedgerExpectations expectations;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired RedemptionRequestRepository redemptionRequestRepository;
  @Autowired TestEntityManager entityManager;

  private Long userId;

  @BeforeEach
  void aHolderWithUnits() {
    var user = sampleUserNonMember().id(null).email("ledger-expectations-test@tuleva.ee").build();
    userId = entityManager.persistFlushFind(user).getId();
    subscribe("5000.00", randomUUID());
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void pricedRedemption_isTheCashTheLedgerPricedTheRedemptionAt() {
    var redemptionId = priceRedemption("300.00", BATCH);

    assertThat(expectations.pricedRedemption(redemptionId))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("300.00"));
  }

  @Test
  void pricedRedemption_isEmptyForARedemptionTheLedgerNeverPriced() {
    assertThat(expectations.pricedRedemption(randomUUID())).isEmpty();
  }

  @Test
  void pricedRedemptionBatch_sumsTheLedgerPricingOfEveryRedemptionInTheBatchAndNoOther() {
    priceRedemption("300.00", BATCH);
    priceRedemption("125.50", BATCH);
    priceRedemption("999.00", OTHER_BATCH);

    assertThat(expectations.pricedRedemptionBatch(BATCH))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("425.50"));
  }

  @Test
  void pricedRedemptionBatch_isEmptyForABatchTheLedgerNeverPriced() {
    assertThat(expectations.pricedRedemptionBatch(randomUUID())).isEmpty();
  }

  @Test
  void issuedSubscriptions_sumsTheSubscriptionsIssuedSinceThePreviousBrief() {
    ClockHolder.setClock(Clock.fixed(BRIEF.minusSeconds(600), UTC));
    subscribe("250.00", randomUUID());

    assertThat(expectations.issuedSubscriptions(PREVIOUS_BRIEF, BRIEF))
        .isEqualByComparingTo("250.00");
  }

  @Test
  void bookedReturn_isTheAmountTheLedgerBookedForACancelledPayment() {
    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(PARTY, new BigDecimal("150.00"), paymentId);
    savingsFundLedger.recordPaymentCancelled(PARTY, new BigDecimal("150.00"), paymentId);

    assertThat(expectations.bookedReturn(paymentId))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("150.00"));
  }

  @Test
  void bookedReturn_isTheAmountTheLedgerBookedForABouncedBackPayment() {
    var paymentId = randomUUID();
    savingsFundLedger.bounceBackUnattributedPayment(new BigDecimal("75.50"), paymentId);

    assertThat(expectations.bookedReturn(paymentId))
        .hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("75.50"));
  }

  @Test
  void bookedReturn_isEmptyUntilTheLedgerHasBookedTheReturn() {
    var paymentId = randomUUID();
    savingsFundLedger.recordPaymentReceived(PARTY, new BigDecimal("150.00"), paymentId);

    assertThat(expectations.bookedReturn(paymentId)).isEmpty();
  }

  private UUID priceRedemption(String cash, UUID batchId) {
    var cashAmount = new BigDecimal(cash);
    var units = cashAmount.divide(NAV).setScale(5);
    var request =
        redemptionRequestRepository.save(
            redemptionRequestFixture()
                .userId(userId)
                .status(REDEEMED)
                .fundUnits(units)
                .cashAmount(cashAmount)
                .batchId(batchId)
                .build());
    savingsFundLedger.reserveFundUnitsForRedemption(PARTY, units, request.getId());
    savingsFundLedger.redeemFundUnitsFromReserved(
        PARTY, units, cashAmount, NAV, NAV_DATE, request.getId());
    return request.getId();
  }

  private void subscribe(String cash, UUID paymentId) {
    var cashAmount = new BigDecimal(cash);
    savingsFundLedger.recordPaymentReceived(PARTY, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(PARTY, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        PARTY, cashAmount, cashAmount.divide(NAV).setScale(5), NAV, NAV_DATE, paymentId);
  }
}
