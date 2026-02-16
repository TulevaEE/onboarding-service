package ee.tuleva.onboarding.savings.fund.report;

import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class TrusteeReportRepository {

  private final JdbcClient jdbcClient;

  List<TrusteeReportRow> findAll() {
    String sql =
        """
        WITH daily_units AS (
          SELECT
            CAST(t.transaction_date AS DATE) AS report_date,
            ROUND(COALESCE(SUM(CASE WHEN e.amount > 0 THEN e.amount ELSE 0 END), 0), 3) AS issued_units,
            ROUND(COALESCE(SUM(CASE WHEN e.amount < 0 THEN -e.amount ELSE 0 END), 0), 3) AS redeemed_units
          FROM ledger.entry e
          JOIN ledger.transaction t ON e.transaction_id = t.id
          JOIN ledger.account a ON e.account_id = a.id
          WHERE a.name = :fundUnitsAccountName
            AND a.purpose = 'SYSTEM_ACCOUNT'
          GROUP BY CAST(t.transaction_date AS DATE)
        ),
        daily_eur AS (
          SELECT
            CAST(t.transaction_date AS DATE) AS report_date,
            ROUND(COALESCE(SUM(CASE WHEN a.name = 'SUBSCRIPTIONS' THEN -e.amount ELSE 0 END), 0), 2) AS issued_amount,
            ROUND(COALESCE(SUM(CASE WHEN a.name = 'REDEMPTIONS' THEN e.amount ELSE 0 END), 0), 2) AS redeemed_amount
          FROM ledger.entry e
          JOIN ledger.transaction t ON e.transaction_id = t.id
          JOIN ledger.account a ON e.account_id = a.id
          WHERE a.name IN ('SUBSCRIPTIONS', 'REDEMPTIONS')
            AND a.purpose = 'USER_ACCOUNT'
          GROUP BY CAST(t.transaction_date AS DATE)
        )
        SELECT
          u.report_date AS "reportDate",
          (SELECT iv.value FROM index_values iv
           WHERE iv.key = 'EE0000003283' AND iv.date < u.report_date
           ORDER BY iv.date DESC LIMIT 1) AS "nav",
          u.issued_units AS "issuedUnits",
          COALESCE(eur.issued_amount, 0) AS "issuedAmount",
          u.redeemed_units AS "redeemedUnits",
          COALESCE(eur.redeemed_amount, 0) AS "redeemedAmount",
          SUM(u.issued_units - u.redeemed_units) OVER (ORDER BY u.report_date) AS "totalOutstandingUnits"
        FROM daily_units u
        LEFT JOIN daily_eur eur ON eur.report_date = u.report_date
        ORDER BY u.report_date DESC
        """;

    return jdbcClient
        .sql(sql)
        .param("fundUnitsAccountName", FUND_UNITS_OUTSTANDING.getAccountName())
        .query(TrusteeReportRow.class)
        .list();
  }
}
