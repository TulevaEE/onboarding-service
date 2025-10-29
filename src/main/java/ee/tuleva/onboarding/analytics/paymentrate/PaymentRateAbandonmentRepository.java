package ee.tuleva.onboarding.analytics.paymentrate;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.PAYMENT_RATE_ABANDONMENT;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.auto.AutoEmailRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRateAbandonmentRepository
    implements AutoEmailRepository<PaymentRateAbandonment> {

  private final JdbcClient jdbcClient;

  @Override
  public List<PaymentRateAbandonment> fetch(LocalDate startDate, LocalDate endDate) {

    String sql =
        """
                WITH filtered_event_log AS (
                  SELECT
                    event_log.principal AS personal_code,
                    event_log.data #>> '{path}' AS path,
                    COUNT(*) AS view_count
                  FROM event_log
                  WHERE event_log.type = 'PAGE_VIEW'
                    AND event_log.data #>> '{path}' = '/2nd-pillar-payment-rate'
                    AND event_log."timestamp" >= :startDate
                    AND event_log."timestamp" <  :endDate
                  GROUP BY event_log.principal, event_log.data #>> '{path}'
                ),

                latest_unit_owner_snapshot AS (
                  SELECT
                    unit_owner.personal_id,
                    unit_owner.first_name,
                    unit_owner.last_name,
                    unit_owner.p2_rate,
                    unit_owner.p2_next_rate,
                    unit_owner.p2_next_rate_date,
                    unit_owner.email,
                    unit_owner.language_preference
                  FROM unit_owner
                  WHERE unit_owner.snapshot_date = (SELECT MAX(snapshot_date) FROM unit_owner)
                ),

                last_email AS (
                  SELECT
                    email.personal_code,
                    MAX(email.created_date) AS last_email_sent_date
                  FROM email
                  WHERE email.type = :emailType
                  GROUP BY email.personal_code
                )

                SELECT
                  latest_unit_owner_snapshot.personal_id            AS personal_code,
                  latest_unit_owner_snapshot.first_name             AS first_name,
                  latest_unit_owner_snapshot.last_name              AS last_name,
                  filtered_event_log.view_count                     AS count,
                  filtered_event_log.path                           AS path,
                  latest_unit_owner_snapshot.p2_rate                AS current_rate,
                  latest_unit_owner_snapshot.p2_next_rate           AS pending_rate,
                  latest_unit_owner_snapshot.p2_next_rate_date      AS pending_rate_date,
                  latest_unit_owner_snapshot.email                  AS email,
                  latest_unit_owner_snapshot.language_preference    AS language,
                  last_email.last_email_sent_date                   AS last_email_sent_date
                FROM filtered_event_log
                JOIN latest_unit_owner_snapshot
                  ON filtered_event_log.personal_code = latest_unit_owner_snapshot.personal_id
                LEFT JOIN last_email
                  ON last_email.personal_code = latest_unit_owner_snapshot.personal_id
                WHERE latest_unit_owner_snapshot.p2_rate IN (2, 4)
                  AND (
                    latest_unit_owner_snapshot.p2_next_rate IS NULL
                    OR latest_unit_owner_snapshot.p2_next_rate NOT IN (4, 6)
                  )
                ORDER BY
                  filtered_event_log.view_count DESC;
            """;

    return jdbcClient
        .sql(sql)
        .param("startDate", startDate)
        .param("endDate", endDate)
        .param("emailType", getEmailType().name())
        .query(PaymentRateAbandonment.class)
        .list();
  }

  @Override
  public EmailType getEmailType() {
    return PAYMENT_RATE_ABANDONMENT;
  }
}
