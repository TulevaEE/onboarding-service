package db.migration;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.ADJUSTMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_ACQUISITION_COST_EUR;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.RoundingMode.HALF_UP;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.AcquisitionCostRecorded;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.AlreadyBackfilled;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.Backfilled;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.TransferNotFound;
import ee.tuleva.onboarding.OnboardingServiceApplication;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.ledger.SavingsFundLedgerStackConfiguration;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.MutableClock;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = OnboardingServiceApplication.class)
@Import(SavingsFundLedgerStackConfiguration.class)
class V1_283BackfillFirstUnitTransferCostTest {

  private static final BigDecimal ONE_UNIT = new BigDecimal("1.00000");
  private static final BigDecimal A_GIFT = new BigDecimal("0.00");
  private static final LocalDate PRICED_ON = LocalDate.parse("2020-01-01");

  @Autowired LedgerService ledgerService;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;
  @Autowired DataSource dataSource;

  PartyRef giver = new PartyRef(PERSON, "38888888888");
  PartyRef recipient = new PartyRef(PERSON, "39999999999");

  MutableClock clock = new MutableClock();

  @BeforeEach
  void onboardTheRecipient() throws SQLException {
    assumeTrue(isPostgres(), "The backfill only runs on PostgreSQL");
    ClockHolder.setClock(clock);
    ledgerService.initializeAccounts(recipient);
  }

  @AfterEach
  void letTimeRunOnItsOwnAgain() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void backfillMovesWhatTheGiversUnitsCostOnAverageToTheRecipient() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("600.00"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);
    givenUnitsWorth(giver, new BigDecimal("300.00"), new BigDecimal("10.00000"));

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new Backfilled(transferId, ONE_UNIT, new BigDecimal("10.00")));
    assertThat(holding(giver, SUBSCRIPTIONS)).isEqualByComparingTo("1290.00");
    assertThat(holding(recipient, SUBSCRIPTIONS)).isEqualByComparingTo("10.00");
  }

  @Test
  void backfillLeavesTheTransferWithBothUnitsAndCostOnBothSides() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("50.00000"), new BigDecimal("600.00"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    backfill(transferId, A_GIFT);

    assertThat(entryCountOf(transferId)).isEqualTo(4);
    assertThat(holding(giver, FUND_UNITS)).isEqualByComparingTo("49.00000");
    assertThat(holding(recipient, FUND_UNITS)).isEqualByComparingTo("1.00000");
  }

  @Test
  void backfillRecordsWhatTheUnitsCostTheirRecipientForTax() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    backfill(transferId, A_GIFT);

    assertThat(reload(transferId).findRecipientAcquisitionCost().orElseThrow())
        .isEqualByComparingTo(A_GIFT);
  }

  @Test
  void backfillRunASecondTimeLeavesTheTransferAlone() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);
    backfill(transferId, A_GIFT);

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new AlreadyBackfilled(transferId));
    assertThat(entryCountOf(transferId)).isEqualTo(4);
  }

  @Test
  void backfillOfATransferThatIsNotThereWritesNothing() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID neverRecorded = randomUUID();
    long entriesBefore = entryCount();

    BackfillResult result = backfill(neverRecorded, A_GIFT);

    assertThat(result).isEqualTo(new TransferNotFound(neverRecorded));
    assertThat(entryCount()).isEqualTo(entriesBefore);
  }

  @Test
  void backfillReplaysASubscriptionRecordedUnderTheOldGenericType() {
    givenLegacyUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new Backfilled(transferId, ONE_UNIT, new BigDecimal("10.00")));
    assertThat(holding(recipient, SUBSCRIPTIONS)).isEqualByComparingTo("10.00");
  }

  @Test
  void backfillStopsWhenMoreWasTakenBackThanTheGiversUnitsStillCost() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenRedeemed(giver, new BigDecimal("90.00000"), new BigDecimal("1200.00"));
    givenTakenBackFromWhatTheyPaidIn(giver, new BigDecimal("200.00"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    assertThatThrownBy(() -> backfill(transferId, A_GIFT))
        .isInstanceOf(IllegalStateException.class);

    assertThat(entryCountOf(transferId)).isEqualTo(2);
    assertThat(reload(transferId).findRecipientAcquisitionCost()).isEmpty();
  }

  @Test
  void backfillStopsWhenTheGiversUnitsCostMoreThanIsLeftOfWhatTheyPaidIn() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenTakenBackFromWhatTheyPaidIn(giver, new BigDecimal("1500.00"));
    givenRedeemed(giver, new BigDecimal("100.00000"), new BigDecimal("1000.00"));
    givenUnitsWorth(giver, new BigDecimal("100.00"), new BigDecimal("10.00000"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    assertThatThrownBy(() -> backfill(transferId, A_GIFT))
        .isInstanceOf(IllegalStateException.class);

    assertThat(entryCountOf(transferId)).isEqualTo(2);
    assertThat(reload(transferId).findRecipientAcquisitionCost()).isEmpty();
  }

  @Test
  void backfillReplaysWhatWasBookedTheSameDayJustBeforeTheTransfer() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    clock.tick(1, DAYS);
    Instant theTransfer = Instant.now(clock);
    givenTheGiverPaidInMore(new BigDecimal("1000.00"), theTransfer, theTransfer.minusSeconds(1));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT, theTransfer);

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new Backfilled(transferId, ONE_UNIT, new BigDecimal("20.00")));
  }

  @Test
  void backfillLeavesOutWhatWasBookedAtTheVeryMomentOfTheTransfer() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    clock.tick(1, DAYS);
    Instant theTransfer = Instant.now(clock);
    givenTheGiverPaidInMore(new BigDecimal("1000.00"), theTransfer, theTransfer);
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT, theTransfer);

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new Backfilled(transferId, ONE_UNIT, new BigDecimal("10.00")));
  }

  @Test
  void backfillOpensARecipientsSubscriptionsAccountThatWasNeverCreated() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    givenTheRecipientHasNoSubscriptionsAccount();
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new Backfilled(transferId, ONE_UNIT, new BigDecimal("10.00")));
    assertThat(subscriptionsAccountShapeOf(recipient))
        .isEqualTo(new AccountShape("SUBSCRIPTIONS", "USER_ACCOUNT", "INCOME", "EUR"));
    assertThat(holding(recipient, SUBSCRIPTIONS)).isEqualByComparingTo("10.00");
  }

  @Test
  void backfillStopsWhenTheReplayedHistoryCoversFewerUnitsThanTheTransfer() {
    givenUnitsWorthRecordedAs(
        FUND_SUBSCRIPTION, giver, new BigDecimal("100.00"), new BigDecimal("0.40000"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);

    assertThatThrownBy(() -> backfill(transferId, A_GIFT))
        .isInstanceOf(IllegalStateException.class);

    assertThat(entryCountOf(transferId)).isEqualTo(2);
    assertThat(reload(transferId).findRecipientAcquisitionCost()).isEmpty();
  }

  @Test
  void backfillCompletesATransferWhoseCostMovedButWhoseAcquisitionCostWasNeverRecorded() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID transferId = givenTheTransferBeforeTheCostFollowedTheUnits(ONE_UNIT);
    backfill(transferId, A_GIFT);
    givenTheAcquisitionCostWasNeverRecorded(transferId);

    BackfillResult result = backfill(transferId, A_GIFT);

    assertThat(result).isEqualTo(new AcquisitionCostRecorded(transferId));
    assertThat(entryCountOf(transferId)).isEqualTo(4);
    assertThat(reload(transferId).findRecipientAcquisitionCost().orElseThrow())
        .isEqualByComparingTo(A_GIFT);
  }

  @Test
  void backfillStopsWhenOnlyOneSideOfTheTransferMovedUnits() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID transferId =
        recordRawTransaction(
            UNIT_TRANSFER,
            transferMetadata(),
            leg(giver, FUND_UNITS, ONE_UNIT),
            unitsOutstanding(ONE_UNIT.negate()));

    assertThatThrownBy(() -> backfill(transferId, A_GIFT))
        .isInstanceOf(IllegalStateException.class);

    assertThat(entryCountOf(transferId)).isEqualTo(2);
    assertThat(reload(transferId).findRecipientAcquisitionCost()).isEmpty();
  }

  @Test
  void backfillStopsWhenBothSidesOfTheTransferGainedUnits() {
    givenUnitsWorth(giver, new BigDecimal("1000.00"), new BigDecimal("100.00000"));
    UUID transferId =
        recordRawTransaction(
            UNIT_TRANSFER,
            transferMetadata(),
            leg(giver, FUND_UNITS, ONE_UNIT),
            leg(recipient, FUND_UNITS, ONE_UNIT));

    assertThatThrownBy(() -> backfill(transferId, A_GIFT))
        .isInstanceOf(IllegalStateException.class);

    assertThat(entryCountOf(transferId)).isEqualTo(2);
    assertThat(reload(transferId).findRecipientAcquisitionCost()).isEmpty();
  }

  private BackfillResult backfill(UUID transferId, BigDecimal recipientAcquisitionCost) {
    entityManager.flush();
    Connection connection = DataSourceUtils.getConnection(dataSource);
    try {
      BackfillResult result =
          V1_283__backfill_first_unit_transfer_cost.backfill(
              connection, transferId, recipientAcquisitionCost);
      entityManager.clear();
      return result;
    } catch (SQLException databaseRefused) {
      throw new IllegalStateException(databaseRefused);
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  private UUID givenTheTransferBeforeTheCostFollowedTheUnits(BigDecimal units) {
    clock.tick(1, DAYS);
    return givenTheTransferBeforeTheCostFollowedTheUnits(units, Instant.now(clock));
  }

  private UUID givenTheTransferBeforeTheCostFollowedTheUnits(BigDecimal units, Instant moment) {
    return recordRawTransaction(
        UNIT_TRANSFER,
        moment,
        moment,
        transferMetadata(),
        leg(giver, FUND_UNITS, units),
        leg(recipient, FUND_UNITS, units.negate()));
  }

  private String transferMetadata() {
    return """
        {"operationType": "%s", "partyCode": "%s", "partyType": "%s", \
        "recipientCode": "%s", "recipientType": "%s"}"""
        .formatted(
            UNIT_TRANSFER.name(),
            giver.code(),
            giver.type().name(),
            recipient.code(),
            recipient.type().name());
  }

  private void givenTakenBackFromWhatTheyPaidIn(PartyRef party, BigDecimal amount) {
    clock.tick(1, DAYS);
    savingsFundLedger.recordAdjustment(
        SUBSCRIPTIONS.name(),
        party,
        INCOMING_PAYMENTS_CLEARING.name(),
        null,
        amount,
        randomUUID(),
        "Payment returned to the payer");
  }

  private void givenTheGiverPaidInMore(
      BigDecimal amount, Instant transactionDate, Instant createdAt) {
    recordRawTransaction(
        ADJUSTMENT,
        transactionDate,
        createdAt,
        """
        {"operationType": "%s"}"""
            .formatted(ADJUSTMENT.name()),
        leg(giver, SUBSCRIPTIONS, amount.negate()),
        incomingPayments(amount));
  }

  private void givenUnitsWorth(PartyRef party, BigDecimal cashAmount, BigDecimal fundUnits) {
    UUID paymentId = randomUUID();
    BigDecimal boughtAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    clock.tick(1, DAYS);
    savingsFundLedger.recordPaymentReceived(party, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, cashAmount, paymentId);
    savingsFundLedger.issueFundUnitsFromReserved(
        party, cashAmount, fundUnits, boughtAt, PRICED_ON, paymentId);
  }

  private void givenRedeemed(PartyRef party, BigDecimal fundUnits, BigDecimal cashAmount) {
    UUID redemptionRequestId = randomUUID();
    BigDecimal soldAt = cashAmount.divide(fundUnits, 5, HALF_UP);
    clock.tick(1, DAYS);
    savingsFundLedger.reserveFundUnitsForRedemption(party, fundUnits, redemptionRequestId);
    clock.tick(1, DAYS);
    savingsFundLedger.redeemFundUnitsFromReserved(
        party, fundUnits, cashAmount, soldAt, PRICED_ON, redemptionRequestId);
  }

  private void givenLegacyUnitsWorth(PartyRef party, BigDecimal cashAmount, BigDecimal fundUnits) {
    givenUnitsWorthRecordedAs(TRANSFER, party, cashAmount, fundUnits);
  }

  private void givenUnitsWorthRecordedAs(
      TransactionType transactionType,
      PartyRef party,
      BigDecimal cashAmount,
      BigDecimal fundUnits) {
    UUID paymentId = randomUUID();
    clock.tick(1, DAYS);
    savingsFundLedger.recordPaymentReceived(party, cashAmount, paymentId);
    savingsFundLedger.reservePaymentForSubscription(party, cashAmount, paymentId);
    recordRawTransaction(
        transactionType,
        """
        {"operationType": "%s"}"""
            .formatted(FUND_SUBSCRIPTION.name()),
        leg(party, CASH_RESERVED, cashAmount),
        leg(party, SUBSCRIPTIONS, cashAmount.negate()),
        leg(party, FUND_UNITS, fundUnits.negate()),
        unitsOutstanding(fundUnits));
  }

  private UUID recordRawTransaction(TransactionType transactionType, String metadata, Leg... legs) {
    clock.tick(1, DAYS);
    Instant moment = Instant.now(clock);
    return recordRawTransaction(transactionType, moment, moment, metadata, legs);
  }

  private UUID recordRawTransaction(
      TransactionType transactionType,
      Instant transactionDate,
      Instant createdAt,
      String metadata,
      Leg... legs) {
    entityManager.flush();
    UUID transactionId =
        jdbcClient
            .sql(
                """
                INSERT INTO ledger.transaction
                       (transaction_type, transaction_date, metadata, created_at)
                VALUES (CAST(:transactionType AS ledger.transaction_type), :transactionDate,
                        CAST(:metadata AS jsonb), :createdAt)
                RETURNING id
                """)
            .param("transactionType", transactionType.name())
            .param("transactionDate", LocalDateTime.ofInstant(transactionDate, UTC))
            .param("createdAt", LocalDateTime.ofInstant(createdAt, UTC))
            .param("metadata", metadata)
            .query(UUID.class)
            .single();
    for (Leg leg : legs) {
      jdbcClient
          .sql(
              """
              INSERT INTO ledger.entry (account_id, transaction_id, amount, asset_type)
              SELECT a.id, :transactionId, :amount, a.asset_type
                FROM ledger.account a WHERE a.id = :accountId
              """)
          .param("transactionId", transactionId)
          .param("amount", leg.amount())
          .param("accountId", leg.accountId())
          .update();
    }
    return transactionId;
  }

  private Leg leg(PartyRef party, UserAccount userAccount, BigDecimal amount) {
    return new Leg(
        ledgerService.getPartyAccount(party.code(), party.type(), userAccount).getId(), amount);
  }

  private Leg unitsOutstanding(BigDecimal amount) {
    return new Leg(ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100).getId(), amount);
  }

  private Leg incomingPayments(BigDecimal amount) {
    return new Leg(
        ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100).getId(), amount);
  }

  private BigDecimal holding(PartyRef party, UserAccount userAccount) {
    return ledgerService
        .getPartyAccount(party.code(), party.type(), userAccount)
        .getBalance()
        .negate();
  }

  private void givenTheRecipientHasNoSubscriptionsAccount() {
    entityManager.flush();
    jdbcClient
        .sql(
            """
            DELETE FROM ledger.account a
             USING ledger.party p
             WHERE p.id = a.owner_party_id AND p.owner_id = :ownerId AND a.name = :accountName
            """)
        .param("ownerId", recipient.code())
        .param("accountName", SUBSCRIPTIONS.name())
        .update();
    entityManager.clear();
  }

  private void givenTheAcquisitionCostWasNeverRecorded(UUID transactionId) {
    entityManager.flush();
    jdbcClient
        .sql("UPDATE ledger.transaction SET metadata = metadata - :key WHERE id = :transactionId")
        .param("key", RECIPIENT_ACQUISITION_COST_EUR.getKey())
        .param("transactionId", transactionId)
        .update();
    entityManager.clear();
  }

  private AccountShape subscriptionsAccountShapeOf(PartyRef party) {
    return jdbcClient
        .sql(
            """
            SELECT a.name, a.purpose::text AS purpose, a.account_type::text AS account_type,
                   a.asset_type::text AS asset_type
              FROM ledger.account a
              JOIN ledger.party p ON p.id = a.owner_party_id
             WHERE p.owner_id = :ownerId AND a.name = :accountName
            """)
        .param("ownerId", party.code())
        .param("accountName", SUBSCRIPTIONS.name())
        .query(
            (rs, row) ->
                new AccountShape(
                    rs.getString("name"),
                    rs.getString("purpose"),
                    rs.getString("account_type"),
                    rs.getString("asset_type")))
        .single();
  }

  private LedgerTransaction reload(UUID transactionId) {
    entityManager.clear();
    return entityManager.find(LedgerTransaction.class, transactionId);
  }

  private long entryCountOf(UUID transactionId) {
    return jdbcClient
        .sql("SELECT count(*) FROM ledger.entry WHERE transaction_id = :transactionId")
        .param("transactionId", transactionId)
        .query(Long.class)
        .single();
  }

  private long entryCount() {
    return jdbcClient.sql("SELECT count(*) FROM ledger.entry").query(Long.class).single();
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }

  private record Leg(UUID accountId, BigDecimal amount) {}

  private record AccountShape(String name, String purpose, String accountType, String assetType) {}
}
