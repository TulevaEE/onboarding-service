package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.ISSUED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionVerdict.TakeoverSignal.NEW_IBAN;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundPayment.Status;
import ee.tuleva.onboarding.savings.fund.OwnDepositHistory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DataJpaTest
@Import({NewPayoutIbanCheck.class, OwnDepositHistory.class})
class NewPayoutIbanCheckIT {

  private static final PartyId PARTY = new PartyId(PERSON, "38888888888");
  private static final PartyId COMPANY = new PartyId(LEGAL_ENTITY, "10000000");
  private static final String PAYOUT_IBAN = "EE111111111111111111";
  private static final String OTHER_IBAN = "EE222222222222222222";
  private static final Instant REQUESTED_AT = Instant.parse("2026-11-20T10:00:00Z");
  private static final Instant A_YEAR_BEFORE = REQUESTED_AT.minus(365, DAYS);

  @Autowired NewPayoutIbanCheck newPayoutIbanCheck;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void aPayoutIbanFirstPaidInFromWithinThirtyDaysAfterAnotherIbanIsNew() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(29, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).contains(NEW_IBAN);
  }

  @Test
  void aPayoutIbanFirstPaidInFromExactlyThirtyDaysBeforeTheRequestIsStillNew() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(30, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).contains(NEW_IBAN);
  }

  @Test
  void aPayoutIbanFirstPaidInFromThirtyOneDaysBeforeTheRequestIsNotNew() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(31, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void thePartysOnlyIbanIsNotNew() {
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(5, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void aWhitelistedPayoutIbanThePartyNeverPaidInFromIsNotNew() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, "EE333333333333333333", REQUESTED_AT.minus(5, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void aPayoutIbanThePartyPaidInFromBeforeAnyOtherIsNotNew() {
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(20, DAYS));
    ownDeposit(PARTY, OTHER_IBAN, REQUESTED_AT.minus(10, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void aPayoutIbanIsAsOldAsItsFirstDepositNotItsLatest() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(60, DAYS));
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(2, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void anEarlierDepositFromTheSameIbanSpelledDifferentlyMakesItNotNew() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, "ee11 1111 1111 1111 1111", REQUESTED_AT.minus(60, DAYS));
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(2, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void anIbanAThirdPartyPaidInFromIsNotAnotherIbanOfTheParty() {
    deposit(PARTY, OTHER_IBAN, "38501010002", PROCESSED, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(5, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void anIbanWhoseDepositWasReturnedIsStillAnEarlierIbanOfTheParty() {
    deposit(PARTY, OTHER_IBAN, PARTY.code(), RETURNED, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(5, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).contains(NEW_IBAN);
  }

  @Test
  void anEarlierDepositNotYetIssuedWhenTheRequestCameIsStillAnEarlierIbanOfTheParty() {
    deposit(PARTY, OTHER_IBAN, PARTY.code(), RECEIVED, REQUESTED_AT.minus(20, DAYS));
    deposit(PARTY, "EE333333333333333333", PARTY.code(), VERIFIED, REQUESTED_AT.minus(15, DAYS));
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(5, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).contains(NEW_IBAN);
  }

  @Test
  void aPayoutIbansFirstDepositCountsWhateverHappenedToItSince() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    deposit(PARTY, PAYOUT_IBAN, PARTY.code(), RETURNED, REQUESTED_AT.minus(60, DAYS));
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.minus(5, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void depositsStillBeingIssuedCountAsThePartysOwn() {
    deposit(PARTY, OTHER_IBAN, PARTY.code(), ISSUED, A_YEAR_BEFORE);
    deposit(PARTY, PAYOUT_IBAN, PARTY.code(), RESERVED, REQUESTED_AT.minus(1, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).contains(NEW_IBAN);
  }

  @Test
  void aPayoutIbanFirstPaidInFromAfterTheRequestIsNotNew() {
    ownDeposit(PARTY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(PARTY, PAYOUT_IBAN, REQUESTED_AT.plus(1, DAYS));

    assertThat(newPayoutIbanCheck.raisedBy(request(PARTY))).isEmpty();
  }

  @Test
  void aCompanysPayoutIbanIsJudgedByTheCompanysOwnDeposits() {
    ownDeposit(COMPANY, OTHER_IBAN, A_YEAR_BEFORE);
    ownDeposit(COMPANY, PAYOUT_IBAN, REQUESTED_AT.minus(10, DAYS));
    ownDeposit(PARTY, PAYOUT_IBAN, A_YEAR_BEFORE);

    assertThat(newPayoutIbanCheck.raisedBy(request(COMPANY))).contains(NEW_IBAN);
  }

  private void ownDeposit(PartyId party, String remitterIban, Instant recordedAt) {
    deposit(party, remitterIban, party.code(), PROCESSED, recordedAt);
  }

  private void deposit(
      PartyId party,
      String remitterIban,
      String remitterIdCode,
      Status status,
      Instant recordedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO saving_fund_payment
          (id, amount, currency, remitter_iban, remitter_id_code, remitter_name,
           party_type, party_code, status, created_at)
        VALUES
          (:id, 100.00, 'EUR', :remitter_iban, :remitter_id_code, 'Test Remitter',
           :party_type, :party_code, :status, :created_at)
        """,
        Map.of(
            "id", UUID.randomUUID(),
            "remitter_iban", remitterIban,
            "remitter_id_code", remitterIdCode,
            "party_type", party.type().name(),
            "party_code", party.code(),
            "status", status.name(),
            "created_at", Timestamp.from(recordedAt)));
  }

  private static RedemptionRequest request(PartyId party) {
    return redemptionRequestFixture()
        .id(UUID.randomUUID())
        .partyType(party.type())
        .partyCode(party.code())
        .customerIban(PAYOUT_IBAN)
        .requestedAt(REQUESTED_AT)
        .build();
  }
}
