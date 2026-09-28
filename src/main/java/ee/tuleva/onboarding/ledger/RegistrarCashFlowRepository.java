package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_CONTRIBUTION;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.REGISTRAR_PAYOUT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNCLASSIFIED_BANK_ENTRY;
import static ee.tuleva.onboarding.ledger.SavingsFundLedger.MetadataKey.DESCRIPTION;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.REGISTRAR_CASH_SETTLEMENT;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class RegistrarCashFlowRepository {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
  private static final JsonMapper METADATA_READER = JsonMapper.builder().build();
  private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() {};

  private static final String CONTRIBUTIONS_BOOKED_BETWEEN =
      """
      SELECT t.transaction_date, e.amount
      FROM ledger.entry e
      JOIN ledger.account a ON e.account_id = a.id
      JOIN ledger.transaction t ON e.transaction_id = t.id
      WHERE a.name = :registrarAccount
        AND a.purpose = 'SYSTEM_ACCOUNT'
        AND t.transaction_type = CAST(:transactionType AS ledger.transaction_type)
        AND t.transaction_date >= :fromInclusive
        AND t.transaction_date < :toExclusive
      ORDER BY t.transaction_date, t.id
      """;

  private static final String PAYOUTS_BOOKED_BETWEEN =
      """
      SELECT t.transaction_date, e.amount, t.metadata, suspense.metadata AS suspense_metadata
      FROM ledger.entry e
      JOIN ledger.account a ON e.account_id = a.id
      JOIN ledger.transaction t ON e.transaction_id = t.id
      LEFT JOIN ledger.transaction suspense
        ON suspense.external_reference = t.external_reference
       AND suspense.transaction_type = CAST(:suspenseType AS ledger.transaction_type)
      WHERE a.name = :registrarAccount
        AND a.purpose = 'SYSTEM_ACCOUNT'
        AND t.transaction_type = CAST(:transactionType AS ledger.transaction_type)
        AND t.transaction_date >= :fromInclusive
        AND t.transaction_date < :toExclusive
      ORDER BY t.transaction_date, t.id
      """;

  private final JdbcClient jdbcClient;

  public List<RegistrarContribution> findContributions(
      TulevaFund fund, LocalDate fromInclusive, LocalDate toInclusive) {
    return bookedBetween(
            CONTRIBUTIONS_BOOKED_BETWEEN, REGISTRAR_CONTRIBUTION, fund, fromInclusive, toInclusive)
        .query(
            (rs, rowNum) ->
                new RegistrarContribution(bookingDate(rs), rs.getBigDecimal("amount").negate()))
        .list();
  }

  public List<RegistrarPayout> findPayouts(
      TulevaFund fund, LocalDate fromInclusive, LocalDate toInclusive) {
    return bookedBetween(PAYOUTS_BOOKED_BETWEEN, REGISTRAR_PAYOUT, fund, fromInclusive, toInclusive)
        .param("suspenseType", UNCLASSIFIED_BANK_ENTRY.name())
        .query(
            (rs, rowNum) ->
                new RegistrarPayout(
                    bookingDate(rs),
                    rs.getBigDecimal("amount"),
                    RegistrarPayoutReason.fromRemittance(remittance(rs))))
        .list();
  }

  public Optional<LocalDate> findFirstCashBookingDate(TulevaFund fund) {
    return jdbcClient
        .sql(
            """
            SELECT MIN(t.transaction_date) AS first_booking
            FROM ledger.entry e
            JOIN ledger.account a ON e.account_id = a.id
            JOIN ledger.transaction t ON e.transaction_id = t.id
            WHERE a.name = :cashAccount
              AND a.purpose = 'SYSTEM_ACCOUNT'
            """)
        .param("cashAccount", FUND_INVESTMENT_CASH_CLEARING.getAccountName(fund))
        .query((rs, rowNum) -> rs.getTimestamp("first_booking"))
        .optional()
        .map(firstBooking -> firstBooking.toInstant().atZone(ESTONIAN_ZONE).toLocalDate());
  }

  private StatementSpec bookedBetween(
      String sql,
      LedgerTransaction.TransactionType transactionType,
      TulevaFund fund,
      LocalDate fromInclusive,
      LocalDate toInclusive) {
    return jdbcClient
        .sql(sql)
        .param("registrarAccount", REGISTRAR_CASH_SETTLEMENT.getAccountName(fund))
        .param("transactionType", transactionType.name())
        .param("fromInclusive", startOf(fromInclusive))
        .param("toExclusive", startOf(toInclusive.plusDays(1)));
  }

  private static Timestamp startOf(LocalDate day) {
    return Timestamp.from(day.atStartOfDay(ESTONIAN_ZONE).toInstant());
  }

  private static LocalDate bookingDate(ResultSet rs) throws SQLException {
    return rs.getTimestamp("transaction_date").toInstant().atZone(ESTONIAN_ZONE).toLocalDate();
  }

  private static @Nullable String remittance(ResultSet rs) throws SQLException {
    var suspenseMetadata = rs.getString("suspense_metadata");
    return description(suspenseMetadata != null ? suspenseMetadata : rs.getString("metadata"));
  }

  private static @Nullable String description(@Nullable String metadataJson) {
    if (metadataJson == null) {
      return null;
    }
    var description = METADATA_READER.readValue(metadataJson, METADATA).get(DESCRIPTION.getKey());
    return description instanceof String text ? text : null;
  }
}
