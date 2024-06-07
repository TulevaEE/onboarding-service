package ee.tuleva.onboarding.analytics.earlywithdrawals;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnalyticsEarlyWithdrawalsRepository {

  private final JdbcClient jdbcClient;

  public List<AnalyticsEarlyWithdrawal> fetchEarlyWithdrawals(
      LocalDate startDate, LocalDate endDate) {
    String sql =
        """
            SELECT
                w.personal_id AS personalCode,
                w.first_name AS firstName,
                w.last_name AS lastName,
                w.email,
                w.language,
                w.early_withdrawal_date AS earlyWithdrawalDate,
                w.early_withdrawal_status AS earlyWithdrawalStatus,
                MAX(em.created_date) AS lastEmailSentDate
            FROM (
                SELECT
                    tuk75.personal_id,
                    tuk75.first_name,
                    tuk75.last_name,
                    tuk75.email,
                    tuk75.language,
                    tuk75.early_withdrawal_date,
                    tuk75.early_withdrawal_status
                FROM
                    analytics.tuk75
                WHERE
                    tuk75.early_withdrawal_date >= :startDate AND
                    tuk75.early_withdrawal_date < :endDate AND
                    tuk75.early_withdrawal_status = 'A'
                UNION ALL
                SELECT
                    tuk00.personal_id,
                    tuk00.first_name,
                    tuk00.last_name,
                    tuk00.email,
                    tuk00.language,
                    tuk00.early_withdrawal_date,
                    tuk00.early_withdrawal_status
                FROM
                    analytics.tuk00
                WHERE
                    tuk00.early_withdrawal_date >= :startDate AND
                    tuk00.early_withdrawal_date < :endDate AND
                    tuk00.early_withdrawal_status = 'A'
            ) w
            LEFT JOIN public.email em ON w.personal_id = em.personal_code
            WHERE
                em.type = 'EARLY_WITHDRAWAL' or em.type IS NULL -- type is null = no email sent yet
            GROUP BY
                w.personal_id, w.first_name, w.last_name,
                w.email, w.language, w.early_withdrawal_date, w.early_withdrawal_status
            """;

    return jdbcClient
        .sql(sql)
        .param("startDate", startDate)
        .param("endDate", endDate)
        .query(AnalyticsEarlyWithdrawal.class)
        .list();
  }
}
