package db.migration;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose.USER_ACCOUNT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRANSFER;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNIT_TRANSFER;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.OPERATION_TYPE;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.RECIPIENT_ACQUISITION_COST_EUR;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.SUBSCRIPTIONS;
import static java.math.BigDecimal.ZERO;

import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.AcquisitionCostRecorded;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.AlreadyBackfilled;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.Backfilled;
import db.migration.V1_283__backfill_first_unit_transfer_cost.BackfillResult.TransferNotFound;
import ee.tuleva.onboarding.ledger.GiverCostBasis;
import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.UnitHoldingChange;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class V1_283__backfill_first_unit_transfer_cost extends BaseJavaMigration {

  private static final Logger log =
      LoggerFactory.getLogger(V1_283__backfill_first_unit_transfer_cost.class);

  private static final String POSTGRESQL = "PostgreSQL";

  private static final UUID THE_ONLY_TRANSFER_RECORDED_WITHOUT_ITS_COST =
      UUID.fromString("9671900f-ba7e-4ae5-bb03-d68953b130d2");
  private static final BigDecimal IT_WAS_A_GIFT = new BigDecimal("0.00");

  private static final String THE_TRANSFER_EXISTS =
      """
      SELECT 1 FROM ledger.transaction
       WHERE id = ? AND transaction_type = '%s'
      """
          .formatted(UNIT_TRANSFER.name());

  private static final String ITS_COST_ALREADY_MOVED =
      """
      SELECT 1 FROM ledger.entry e
        JOIN ledger.account a ON a.id = e.account_id
       WHERE e.transaction_id = ? AND a.name = '%s'
      """
          .formatted(SUBSCRIPTIONS.name());

  private static final String BOTH_SIDES_OF_THE_TRANSFER =
      """
      SELECT a.owner_party_id, e.amount
        FROM ledger.entry e
        JOIN ledger.account a ON a.id = e.account_id
       WHERE e.transaction_id = ? AND a.name = '%s'
       ORDER BY e.amount DESC
      """
          .formatted(FUND_UNITS.name());

  private static final String THE_GIVERS_HISTORY_BEFORE_THE_TRANSFER =
      """
      WITH transfer AS (
        SELECT id, transaction_date, created_at FROM ledger.transaction WHERE id = ?
      ),
      giver_accounts AS (
        SELECT id, name FROM ledger.account
         WHERE owner_party_id = ? AND name IN ('%s', '%s', '%s')
      )
      SELECT t.id AS transaction_id,
             CASE WHEN t.transaction_type = '%s'
                  THEN t.metadata->>'%s'
                  ELSE t.transaction_type::text END AS operation_type,
             SUM(CASE WHEN giver_accounts.name IN ('%s', '%s') THEN -e.amount ELSE 0 END) AS units,
             SUM(CASE WHEN giver_accounts.name = '%s' THEN -e.amount ELSE 0 END) AS cost
        FROM ledger.entry e
        JOIN giver_accounts ON giver_accounts.id = e.account_id
        JOIN ledger.transaction t ON t.id = e.transaction_id
       CROSS JOIN transfer
       WHERE t.id <> transfer.id
         AND (t.transaction_date, t.created_at) < (transfer.transaction_date, transfer.created_at)
       GROUP BY t.id
       ORDER BY t.transaction_date, t.created_at, t.id
      """
          .formatted(
              FUND_UNITS.name(),
              FUND_UNITS_RESERVED.name(),
              SUBSCRIPTIONS.name(),
              TRANSFER.name(),
              OPERATION_TYPE.getKey(),
              FUND_UNITS.name(),
              FUND_UNITS_RESERVED.name(),
              SUBSCRIPTIONS.name());

  private static final String THE_ACQUISITION_COST_IS_RECORDED =
      """
      SELECT 1 FROM ledger.transaction
       WHERE id = ? AND metadata->>'%s' IS NOT NULL
      """
          .formatted(RECIPIENT_ACQUISITION_COST_EUR.getKey());

  private static final String OPEN_A_SUBSCRIPTIONS_ACCOUNT_IF_ABSENT =
      """
      INSERT INTO ledger.account (owner_party_id, name, purpose, account_type, asset_type)
      VALUES (?, '%s',
              CAST('%s' AS ledger.account_purpose),
              CAST('%s' AS ledger.account_type),
              CAST('%s' AS ledger.asset_type))
      ON CONFLICT DO NOTHING
      """
          .formatted(
              SUBSCRIPTIONS.name(),
              USER_ACCOUNT.name(),
              SUBSCRIPTIONS.getAccountType().name(),
              SUBSCRIPTIONS.getAssetType().name());

  private static final String THE_SUBSCRIPTIONS_ACCOUNT =
      """
      SELECT id FROM ledger.account
       WHERE owner_party_id = ? AND name = '%s' AND purpose = '%s'
         AND account_type = '%s' AND asset_type = '%s'
      """
          .formatted(
              SUBSCRIPTIONS.name(),
              USER_ACCOUNT.name(),
              SUBSCRIPTIONS.getAccountType().name(),
              SUBSCRIPTIONS.getAssetType().name());

  private static final String MOVE_THE_COST =
      """
      INSERT INTO ledger.entry (account_id, transaction_id, amount, asset_type)
      SELECT a.id, CAST(? AS uuid), CAST(? AS numeric), a.asset_type
        FROM ledger.account a
       WHERE a.id = ?
      """;

  private static final String RECORD_WHAT_THE_UNITS_COST_THEIR_RECIPIENT =
      """
      UPDATE ledger.transaction
         SET metadata = jsonb_set(metadata, '{%s}', to_jsonb(CAST(? AS numeric)))
       WHERE id = ?
      """
          .formatted(RECIPIENT_ACQUISITION_COST_EUR.getKey());

  public sealed interface BackfillResult {
    record TransferNotFound(UUID transactionId) implements BackfillResult {}

    record AlreadyBackfilled(UUID transactionId) implements BackfillResult {}

    record AcquisitionCostRecorded(UUID transactionId) implements BackfillResult {}

    record Backfilled(UUID transactionId, BigDecimal units, BigDecimal contributionMoved)
        implements BackfillResult {}
  }

  @Override
  public void migrate(Context context) throws Exception {
    if (!isPostgreSql(context)) {
      return;
    }
    backfill(context.getConnection(), THE_ONLY_TRANSFER_RECORDED_WITHOUT_ITS_COST, IT_WAS_A_GIFT);
  }

  public static BackfillResult backfill(
      Connection connection, UUID transactionId, BigDecimal recipientAcquisitionCost)
      throws SQLException {
    if (!answersYes(connection, THE_TRANSFER_EXISTS, transactionId)) {
      log.info("No unit transfer to backfill the cost of: transactionId={}", transactionId);
      return new TransferNotFound(transactionId);
    }
    if (answersYes(connection, ITS_COST_ALREADY_MOVED, transactionId)) {
      if (answersYes(connection, THE_ACQUISITION_COST_IS_RECORDED, transactionId)) {
        log.info("The unit transfer already carries its cost: transactionId={}", transactionId);
        return new AlreadyBackfilled(transactionId);
      }
      recordAcquisitionCost(connection, transactionId, recipientAcquisitionCost);
      log.info(
          "Recorded what the transferred units cost their recipient: transactionId={},"
              + " recipientAcquisitionCostEur={}",
          transactionId,
          recipientAcquisitionCost.toPlainString());
      return new AcquisitionCostRecorded(transactionId);
    }

    TransferSides sides = sidesOf(connection, transactionId);
    List<UnitHoldingChange> history = historyOf(connection, transactionId, sides.giver());
    GiverCostBasis costBasis = GiverCostBasis.replay(history);
    rejectAHistoryThatDoesNotCoverTheUnits(transactionId, costBasis, sides.units());
    rejectACostThatCannotFollowTheUnits(transactionId, costBasis, paidInBefore(history));
    BigDecimal contributionMoved = costBasis.costOf(sides.units());

    UUID giversSubscriptions = subscriptionsAccountOf(connection, sides.giver());
    UUID recipientsSubscriptions = subscriptionsAccountOf(connection, sides.recipient());
    moveCost(connection, transactionId, giversSubscriptions, contributionMoved);
    moveCost(connection, transactionId, recipientsSubscriptions, contributionMoved.negate());
    recordAcquisitionCost(connection, transactionId, recipientAcquisitionCost);

    log.info(
        "Backfilled the cost of the first unit transfer: transactionId={}, units={},"
            + " contributionMoved={}",
        transactionId,
        sides.units().toPlainString(),
        contributionMoved.toPlainString());
    return new Backfilled(transactionId, sides.units(), contributionMoved);
  }

  private static BigDecimal paidInBefore(List<UnitHoldingChange> history) {
    return history.stream().map(UnitHoldingChange::cost).reduce(ZERO, BigDecimal::add);
  }

  private static void rejectAHistoryThatDoesNotCoverTheUnits(
      UUID transactionId, GiverCostBasis costBasis, BigDecimal transferredUnits) {
    if (costBasis.remainingUnits().compareTo(transferredUnits) < 0) {
      throw new IllegalStateException(
          "The giver's replayed unit history does not cover the units they gave away:"
              + " transactionId="
              + transactionId
              + ", replayedUnits="
              + costBasis.remainingUnits().toPlainString()
              + ", transferredUnits="
              + transferredUnits.toPlainString());
    }
  }

  private static void rejectACostThatCannotFollowTheUnits(
      UUID transactionId, GiverCostBasis costBasis, BigDecimal paidIn) {
    if (costBasis.remainingCost().signum() < 0) {
      throw new IllegalStateException(
          "More was taken back from the giver than their remaining units cost: transactionId="
              + transactionId
              + ", remainingCost="
              + costBasis.remainingCost().toPlainString()
              + ", paidIn="
              + paidIn.toPlainString());
    }
    if (costBasis.remainingCost().compareTo(paidIn) > 0) {
      throw new IllegalStateException(
          "The giver's remaining units cost more than what is left of what they paid in:"
              + " transactionId="
              + transactionId
              + ", remainingCost="
              + costBasis.remainingCost().toPlainString()
              + ", paidIn="
              + paidIn.toPlainString());
    }
  }

  private static boolean answersYes(Connection connection, String sql, UUID transactionId)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, transactionId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }

  private static TransferSides sidesOf(Connection connection, UUID transactionId)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(BOTH_SIDES_OF_THE_TRANSFER)) {
      statement.setObject(1, transactionId);
      try (ResultSet rows = statement.executeQuery()) {
        List<UnitLeg> legs = new ArrayList<>();
        while (rows.next()) {
          legs.add(
              new UnitLeg(
                  rows.getObject("owner_party_id", UUID.class), rows.getBigDecimal("amount")));
        }
        if (legs.size() != 2
            || legs.getFirst().amount().signum() <= 0
            || legs.getLast().amount().signum() >= 0) {
          throw new IllegalStateException(
              "A unit transfer moves units from one party to another, but this one does not:"
                  + " transactionId="
                  + transactionId
                  + ", unitLegs="
                  + legs);
        }
        return new TransferSides(
            legs.getFirst().party(), legs.getLast().party(), legs.getFirst().amount());
      }
    }
  }

  private static List<UnitHoldingChange> historyOf(
      Connection connection, UUID transactionId, UUID giver) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(THE_GIVERS_HISTORY_BEFORE_THE_TRANSFER)) {
      statement.setObject(1, transactionId);
      statement.setObject(2, giver);
      try (ResultSet rows = statement.executeQuery()) {
        List<UnitHoldingChange> history = new ArrayList<>();
        while (rows.next()) {
          UUID id = rows.getObject("transaction_id", UUID.class);
          history.add(
              new UnitHoldingChange(
                  id,
                  operationOf(id, rows.getString("operation_type")),
                  rows.getBigDecimal("units"),
                  rows.getBigDecimal("cost")));
        }
        return history;
      }
    }
  }

  private static TransactionType operationOf(UUID transactionId, @Nullable String operationType) {
    if (operationType == null) {
      throw new IllegalStateException(
          "Fund units moved under a transaction that names no operation to replay their cost from:"
              + " transactionId="
              + transactionId);
    }
    return TransactionType.valueOf(operationType);
  }

  private static UUID subscriptionsAccountOf(Connection connection, UUID party)
      throws SQLException {
    try (PreparedStatement opening =
        connection.prepareStatement(OPEN_A_SUBSCRIPTIONS_ACCOUNT_IF_ABSENT)) {
      opening.setObject(1, party);
      opening.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement(THE_SUBSCRIPTIONS_ACCOUNT)) {
      statement.setObject(1, party);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException(
              "A party of the unit transfer has no subscriptions account to move its cost to:"
                  + " partyId="
                  + party);
        }
        return rows.getObject("id", UUID.class);
      }
    }
  }

  private static void moveCost(
      Connection connection, UUID transactionId, UUID account, BigDecimal amount)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(MOVE_THE_COST)) {
      statement.setObject(1, transactionId);
      statement.setBigDecimal(2, amount);
      statement.setObject(3, account);
      int moved = statement.executeUpdate();
      if (moved != 1) {
        throw new IllegalStateException(
            "The cost of the transferred units could not be booked on a subscriptions account:"
                + " transactionId="
                + transactionId
                + ", accountId="
                + account
                + ", entries="
                + moved);
      }
    }
  }

  private static void recordAcquisitionCost(
      Connection connection, UUID transactionId, BigDecimal recipientAcquisitionCost)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(RECORD_WHAT_THE_UNITS_COST_THEIR_RECIPIENT)) {
      statement.setBigDecimal(1, recipientAcquisitionCost);
      statement.setObject(2, transactionId);
      statement.executeUpdate();
    }
  }

  private boolean isPostgreSql(Context context) throws Exception {
    return POSTGRESQL.equals(context.getConnection().getMetaData().getDatabaseProductName());
  }

  private record UnitLeg(UUID party, BigDecimal amount) {}

  private record TransferSides(UUID giver, UUID recipient, BigDecimal units) {}
}
