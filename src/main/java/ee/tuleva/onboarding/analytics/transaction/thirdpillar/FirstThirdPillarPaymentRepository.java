package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import ee.tuleva.onboarding.notification.email.firstpayment.FirstThirdPillarPayment;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FirstThirdPillarPaymentRepository {

  static final String OWN_MONEY_SOURCE = "Osakute väljalase isikult laekumiste alusel";

  private final JdbcClient jdbcClient;

  public Optional<LocalDate> oldestOwnPaymentDate() {
    return jdbcClient
        .sql(
            """
            SELECT MIN(reporting_date) FROM analytics.third_pillar_transactions
            WHERE transaction_source = :ownMoneySource
            """)
        .param("ownMoneySource", OWN_MONEY_SOURCE)
        .query(LocalDate.class)
        .optional();
  }

  public List<FirstThirdPillarPayment> fetchUnemailedFirstPayments(
      LocalDate windowStart, LocalDate adultBirthDateCutoff) {
    String sql =
        """
        WITH own_payments AS (
          SELECT personal_id, reporting_date, transaction_value
          FROM analytics.third_pillar_transactions
          WHERE transaction_source = :ownMoneySource
        ),
        first_payments AS (
          SELECT personal_id, MIN(reporting_date) AS first_payment_date
          FROM own_payments
          GROUP BY personal_id
          HAVING MIN(reporting_date) >= :windowStart
        ),
        first_amounts AS (
          SELECT fp.personal_id, fp.first_payment_date, SUM(op.transaction_value) AS amount
          FROM first_payments fp
          JOIN own_payments op
            ON op.personal_id = fp.personal_id AND op.reporting_date = fp.first_payment_date
          GROUP BY fp.personal_id, fp.first_payment_date
        ),
        latest_unit_owner AS (
          SELECT * FROM unit_owner
          WHERE snapshot_date = (SELECT MAX(snapshot_date) FROM unit_owner)
        )
        SELECT fa.personal_id,
               COALESCE(NULLIF(u.first_name, ''), NULLIF(uo.first_name, '')) AS first_name,
               COALESCE(NULLIF(u.last_name, ''), NULLIF(uo.last_name, '')) AS last_name,
               COALESCE(NULLIF(u.email, ''), NULLIF(uo.email, '')) AS email,
               COALESCE(uo.language_preference, 'EST') AS language_preference,
               fa.amount,
               fa.first_payment_date,
               (u.id IS NOT NULL) AS has_tuleva_user,
               (COALESCE(uo.p2_rava_status, '') <> 'R'
                 AND (uo.personal_id IS NULL
                   OR uo.p2_choice IS NULL
                   OR uo.p2_choice NOT IN ('TUK75', 'TUK00'))) AS suggest_second_pillar,
               (COALESCE(uo.p2_rava_status, '') <> 'R'
                 AND COALESCE(uo.p2_next_rate, uo.p2_rate, 2) < 6) AS suggest_payment_rate,
               (m.id IS NULL) AS suggest_membership,
               (COALESCE(uo.p2_rava_status, '') = 'R') AS left_second_pillar,
               EXISTS (
                 SELECT 1 FROM saving_fund_payment sfp
                 WHERE sfp.party_type = 'PERSON'
                   AND sfp.party_code = fa.personal_id
                   AND sfp.status IN ('ISSUED', 'PROCESSED')) AS saves_in_savings_fund
        FROM first_amounts fa
        LEFT JOIN users u ON u.personal_code = fa.personal_id
        LEFT JOIN latest_unit_owner uo ON uo.personal_id = fa.personal_id
        LEFT JOIN member m ON m.user_id = u.id
        WHERE COALESCE(NULLIF(u.email, ''), NULLIF(uo.email, '')) IS NOT NULL
          AND COALESCE(NULLIF(u.first_name, ''), NULLIF(uo.first_name, '')) IS NOT NULL
          AND COALESCE(NULLIF(u.last_name, ''), NULLIF(uo.last_name, '')) IS NOT NULL
          AND uo.death_date IS NULL
          AND CAST(CONCAT(
                CASE WHEN SUBSTRING(fa.personal_id, 1, 1) IN ('1', '2') THEN '18'
                     WHEN SUBSTRING(fa.personal_id, 1, 1) IN ('3', '4') THEN '19'
                     ELSE '20' END,
                SUBSTRING(fa.personal_id, 2, 2), '-',
                SUBSTRING(fa.personal_id, 4, 2), '-',
                SUBSTRING(fa.personal_id, 6, 2)) AS DATE) <= :adultBirthDateCutoff
          AND NOT EXISTS (
            SELECT 1 FROM email e
            WHERE e.personal_code = fa.personal_id
              AND e.type IN ('THIRD_PILLAR_PAYMENT_ARRIVED',
                             'THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE',
                             'THIRD_PILLAR_PAYMENT_REMINDER_MANDATE'))
          AND NOT EXISTS (
            SELECT 1 FROM third_pillar_payment_arrived_claim c
            WHERE c.personal_code = fa.personal_id)
        ORDER BY fa.first_payment_date, fa.personal_id
        """;

    return jdbcClient
        .sql(sql)
        .param("ownMoneySource", OWN_MONEY_SOURCE)
        .param("windowStart", windowStart)
        .param("adultBirthDateCutoff", adultBirthDateCutoff)
        .query(
            (rs, rowNum) ->
                new FirstThirdPillarPayment(
                    rs.getString("personal_id"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("language_preference"),
                    rs.getBigDecimal("amount"),
                    rs.getObject("first_payment_date", LocalDate.class),
                    rs.getBoolean("has_tuleva_user"),
                    rs.getBoolean("suggest_second_pillar")
                        && !hasReachedRetirementAge(rs.getString("personal_id")),
                    rs.getBoolean("suggest_payment_rate"),
                    rs.getBoolean("suggest_membership"),
                    rs.getBoolean("left_second_pillar"),
                    rs.getBoolean("saves_in_savings_fund")))
        .list();
  }

  private static boolean hasReachedRetirementAge(String personalCode) {
    return PersonalCode.getAge(personalCode) >= PersonalCode.getRetirementAge(personalCode);
  }
}
