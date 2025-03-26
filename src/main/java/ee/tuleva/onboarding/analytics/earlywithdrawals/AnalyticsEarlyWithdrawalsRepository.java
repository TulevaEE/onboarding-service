package ee.tuleva.onboarding.analytics.earlywithdrawals;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_EARLY_WITHDRAWAL;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.auto.AutoEmailRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnalyticsEarlyWithdrawalsRepository
    implements AutoEmailRepository<AnalyticsEarlyWithdrawal> {

  private final JdbcClient jdbcClient;

  @Override
  public List<AnalyticsEarlyWithdrawal> fetch(LocalDate startDate, LocalDate endDate) {
    String sql =
        String.format(
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
                    tuk75.early_withdrawal_status = 'A' AND
                    tuk75.email IS NOT NULL
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
                    tuk00.early_withdrawal_status = 'A' AND
                    tuk00.email IS NOT NULL
            ) w
            LEFT JOIN public.email em ON w.personal_id = em.personal_code
            WHERE
                em.type = '%s' OR em.type IS NULL -- type is null = no email sent yet
            GROUP BY
                w.personal_id, w.first_name, w.last_name,
                w.email, w.language, w.early_withdrawal_date, w.early_withdrawal_status
            """,
            getEmailType());

    LocalDate adjustedStartDate = startDate.minusMonths(1);
    LocalDate adjustedEndDate = endDate.minusMonths(1);

    return jdbcClient
        .sql(sql)
        .param("startDate", adjustedStartDate)
        .param("endDate", adjustedEndDate)
        .query(AnalyticsEarlyWithdrawal.class)
        .list();
  }

  @Override
  public EmailType getEmailType() {
    return SECOND_PILLAR_EARLY_WITHDRAWAL;
  }
}
