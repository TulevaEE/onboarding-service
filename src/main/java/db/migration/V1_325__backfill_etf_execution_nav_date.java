package db.migration;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V1_325__backfill_etf_execution_nav_date extends BaseJavaMigration {

  private static final Logger log =
      LoggerFactory.getLogger(V1_325__backfill_etf_execution_nav_date.class);

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private static final String ETF_FILLS_WITHOUT_A_NAV_DATE =
      """
      SELECT e.id, e.execution_timestamp
        FROM investment_transaction_execution e
        JOIN investment_transaction_order o ON o.id = e.order_id
       WHERE o.instrument_type = 'ETF'
         AND e.nav_date IS NULL
         AND e.execution_timestamp IS NOT NULL
      """;

  private static final String SET_THE_NAV_DATE =
      "UPDATE investment_transaction_execution SET nav_date = ? WHERE id = ?";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement();
        ResultSet fills = select.executeQuery(ETF_FILLS_WITHOUT_A_NAV_DATE);
        PreparedStatement update = context.getConnection().prepareStatement(SET_THE_NAV_DATE)) {
      while (fills.next()) {
        update.setObject(1, tradeDateInTallinn(fills.getObject(2, OffsetDateTime.class)));
        update.setLong(2, fills.getLong(1));
        update.addBatch();
      }
      int backfilled = update.executeBatch().length;
      log.info("Backfilled the nav date of ETF fills: backfilled={}", backfilled);
    }
  }

  private static LocalDate tradeDateInTallinn(OffsetDateTime tradedAt) {
    return tradedAt.atZoneSameInstant(TALLINN).toLocalDate();
  }
}
