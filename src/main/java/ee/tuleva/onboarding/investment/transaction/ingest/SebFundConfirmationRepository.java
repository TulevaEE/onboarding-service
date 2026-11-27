package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class SebFundConfirmationRepository {

  private final JdbcClient jdbcClient;

  Optional<RecordedSebFundConfirmation> findByTradeReference(String tradeReference) {
    return jdbcClient
        .sql(
            """
            SELECT trade_reference, fund_code, instrument_isin, transaction_type,
                   order_received_at, pricing_date, source_file_key
            FROM investment_seb_fund_confirmation
            WHERE trade_reference = :tradeReference
            """)
        .param("tradeReference", tradeReference)
        .query((rs, rowNum) -> toRecorded(rs))
        .optional();
  }

  void insert(RecordedSebFundConfirmation confirmation, Instant now) {
    write(
        """
        INSERT INTO investment_seb_fund_confirmation (
          trade_reference, fund_code, instrument_isin, transaction_type,
          order_received_at, pricing_date, source_file_key, created_at, updated_at)
        VALUES (
          :tradeReference, :fundCode, :isin, :side,
          :orderReceivedAt, :pricingDate, :sourceFileKey, :now, :now)
        """,
        confirmation,
        now);
  }

  void update(RecordedSebFundConfirmation confirmation, Instant now) {
    write(
        """
        UPDATE investment_seb_fund_confirmation
        SET fund_code = :fundCode,
            instrument_isin = :isin,
            transaction_type = :side,
            order_received_at = :orderReceivedAt,
            pricing_date = :pricingDate,
            source_file_key = :sourceFileKey,
            updated_at = :now
        WHERE trade_reference = :tradeReference
        """,
        confirmation,
        now);
  }

  private void write(String sql, RecordedSebFundConfirmation confirmation, Instant now) {
    jdbcClient
        .sql(sql)
        .param("tradeReference", confirmation.tradeReference())
        .param("fundCode", confirmation.fund().name())
        .param("isin", confirmation.isin())
        .param("side", confirmation.side().name())
        .param("orderReceivedAt", Timestamp.from(confirmation.orderReceivedAt()))
        .param("pricingDate", confirmation.pricingDate())
        .param("sourceFileKey", confirmation.sourceFileKey())
        .param("now", Timestamp.from(now))
        .update();
  }

  private static RecordedSebFundConfirmation toRecorded(ResultSet rs) throws SQLException {
    return new RecordedSebFundConfirmation(
        rs.getString("trade_reference"),
        TulevaFund.valueOf(rs.getString("fund_code")),
        rs.getString("instrument_isin"),
        TransactionType.valueOf(rs.getString("transaction_type")),
        rs.getTimestamp("order_received_at").toInstant(),
        rs.getObject("pricing_date", LocalDate.class),
        rs.getString("source_file_key"));
  }
}
