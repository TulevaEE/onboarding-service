package ee.tuleva.onboarding.analytics.anniversary;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.auto.AutoEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.ANNIVERSARY;

@Repository
@RequiredArgsConstructor
public class AnalyticsAnniversaryRepository
    implements AutoEmailRepository<AnalyticsAnniversary> {

  private final JdbcClient jdbcClient;

  @Override
  public List<AnalyticsAnniversary> fetch(LocalDate _startDate, LocalDate _endDate) {
    String sql =
        """
                SELECT
                    w.personal_id AS personalCode,
                    w.email,
                    w.language,
                    BOOL_OR(w.secondPillar) as secondPillar, -- secondPillar is defined as literals in tuk75 and tuk00 subquery to identify which table is II and III pillar
                    BOOL_OR(w.thirdPillar) as thirdPillar, -- same as above but for thirdPillar
                    -- logical OR is true when they appear in secondPillar or thirdPillar queries respectively
                    MAX(w.fullYears) AS fullYears -- whenever they started saving earliest
                FROM (
                    SELECT
                        personal_id,
                        email,
                        language,
                        true as secondPillar,
                        false as thirdPillar,
                        EXTRACT(YEAR FROM AGE(MAX(reporting_date), MIN(reporting_date))) AS fullYears
                    FROM
                        analytics.tuk75
                    WHERE
                        active = 'A' -- selection is active
                        AND early_withdrawal_status IS NULL -- they haven't withdrawn
                        AND share_amount != 0  -- they haven't been given Tuleva via lottery, with no contributions
                        AND email IS NOT NULL
                        AND death_date IS NULL
                    GROUP BY
                        personal_id, email, language
                    HAVING
                        EXTRACT(YEAR FROM AGE(MAX(reporting_date), MIN(reporting_date))) >= 1 -- they have been saving with Tuleva II pillar for at least 1 year
                        AND EXTRACT(MONTH FROM AGE(MAX(reporting_date), MIN(reporting_date))) = 0 -- they have been saving for x years 0 months z days
                        AND MAX(reporting_date) >= (CURRENT_DATE - INTERVAL '1 month') -- since this table contains older records, make sure that it's based on latest reporting date

                    UNION ALL

                    SELECT
                        personal_id,
                        email,
                        language,
                        true as secondPillar,
                        false as thirdPillar,
                        EXTRACT(YEAR FROM AGE(MAX(reporting_date), MIN(reporting_date))) AS fullYears
                    FROM
                        analytics.tuk00
                    WHERE
                        active = 'A'  -- selection is active
                        AND early_withdrawal_status IS NULL -- they haven't withdrawn
                        AND share_amount != 0 -- they haven't been given Tuleva via lottery, with no contributions
                        AND email IS NOT NULL
                        AND death_date IS NULL
                    GROUP BY
                        personal_id, email, language
                    HAVING
                        EXTRACT(YEAR FROM AGE(MAX(reporting_date), MIN(reporting_date))) >= 1 -- they have been saving with Tuleva II pillar for at least 1 year
                        AND EXTRACT(MONTH FROM AGE(MAX(reporting_date), MIN(reporting_date))) = 0 -- they have been saving for x years 0 months z days
                              AND MAX(reporting_date) >= (CURRENT_DATE - INTERVAL '1 month') -- since this table contains older records, make sure that it's based on latest reporting date

                    UNION ALL
       
                    SELECT
                        personal_id,
                        email,
                        language,
                        false as secondPillar,
                        true as thirdPillar,
                        EXTRACT(YEAR FROM AGE(MAX(reporting_date), MIN(reporting_date))) AS fullYears
                    FROM
                        analytics.third_pillar
                    WHERE
                        share_amount != 0 -- they have contributed to third pillar
                        AND email IS NOT NULL
                        AND death_date IS NULL
                    GROUP BY
                        personal_id, first_name, last_name, email, language
                    HAVING
                        EXTRACT(YEAR FROM AGE(MAX(reporting_date), MIN(reporting_date))) >= 1 -- they have been saving with Tuleva III pillar for at least 1 year
                        AND EXTRACT(MONTH FROM AGE(MAX(reporting_date), MIN(reporting_date))) = 0 -- they have been saving for x years 0 months z days
                        AND MAX(reporting_date) >= (CURRENT_DATE - INTERVAL '1 month') -- make sure that it's based on latest reporting date
                ) w
                GROUP BY
                    personalCode, email, language
        """;

    return jdbcClient
        .sql(sql)
        .query(AnalyticsAnniversary.class)
        .list();
  }

  @Override
  public EmailType getEmailType() {
    return ANNIVERSARY;
  }
}
