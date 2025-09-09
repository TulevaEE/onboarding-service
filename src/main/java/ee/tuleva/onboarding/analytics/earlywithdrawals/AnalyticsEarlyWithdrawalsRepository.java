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
            SELECT unit_owner.personal_id         AS personalCode,
                   unit_owner.first_name          AS firstName,
                   unit_owner.last_name           AS lastName,
                   unit_owner.email               AS email,
                   unit_owner.language_preference AS language,
                   unit_owner.p2_rava_date        AS earlyWithdrawalDate,
                   unit_owner.p2_rava_status      AS earlyWithdrawalStatus,
                   MAX(email.created_date)        AS lastEmailSentDate
            FROM unit_owner
                     LEFT JOIN unit_owner_balance ON unit_owner.id = unit_owner_balance.unit_owner_id
                     LEFT JOIN email ON unit_owner.personal_id = email.personal_code
                     AND email.type = '%s'
            WHERE (unit_owner.p2_choice IN ('TUK00', 'TUK75')
                OR unit_owner_balance.security_short_name IN ('TUK00', 'TUK75'))
              AND unit_owner.snapshot_date = (SELECT MAX(snapshot_date) FROM unit_owner)
              AND unit_owner.p2_rava_status = 'A'
              AND unit_owner.email IS NOT NULL
              AND TRIM(unit_owner.email) <> ''
              AND unit_owner.p2_rava_date > :startDate
              AND unit_owner.p2_rava_date <= :endDate
            GROUP BY unit_owner.personal_id,
                     unit_owner.first_name,
                     unit_owner.last_name,
                     unit_owner.email,
                     unit_owner.language_preference,
                     unit_owner.p2_rava_date,
                     unit_owner.p2_rava_status
            ORDER BY unit_owner.personal_id;
            """,
            getEmailType());

    return jdbcClient
        .sql(sql)
        .param("startDate", startDate)
        .param("endDate", endDate)
        .query(AnalyticsEarlyWithdrawal.class)
        .list();
  }

  @Override
  public EmailType getEmailType() {
    return SECOND_PILLAR_EARLY_WITHDRAWAL;
  }
}
