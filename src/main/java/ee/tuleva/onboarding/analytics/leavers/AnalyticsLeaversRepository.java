package ee.tuleva.onboarding.analytics.leavers;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnalyticsLeaversRepository {

  private final JdbcClient jdbcClient;

  public List<AnalyticsLeaver> fetchLeavers(LocalDate startDate, LocalDate endDate) {
    String sql =
        """
        SELECT
            ca.current_fund AS "currentFund",
            ca.new_fund AS "newFund",
            ca.personal_id AS "personalCode",
            ca.first_name AS "firstName",
            ca.last_name AS "lastName",
            ca.share_amount AS "shareAmount",
            ca.share_percentage AS "sharePercentage",
            ca.date_created AS "dateCreated",
            fund.ongoing_charges_figure AS "fundOngoingChargesFigure",
            fund.name_estonian AS "fundNameEstonian",
            mcmp.email AS "email",
            mcmp.keel AS "language",
            mcmp.vanus AS "age",
            MAX(em.created_date) AS "lastEmailSentDate"
        FROM
            analytics.change_application ca
        LEFT JOIN
            public.fund fund ON ca.new_fund = fund.short_name
        LEFT JOIN
            analytics.mv_crm_mailchimp mcmp ON ca.personal_id = mcmp.isikukood
        LEFT JOIN
            public.email em ON ca.personal_id = em.personal_code
        WHERE
            ca.date_created >= :startDate AND
            ca.date_created < :endDate AND
            (ca.current_fund = 'TUK75' OR ca.current_fund = 'TUK00') AND
            ca.new_fund <> 'TUK00' AND
            ca.new_fund <> 'TUK75' AND
            fund.ongoing_charges_figure >= 0.005 AND
            mcmp.vanus < 55 AND
            mcmp.email IS NOT NULL AND
            (mcmp.keel = 'ENG' OR mcmp.keel = 'EST') AND
            ca.share_percentage >= 10 AND
            (em.type = 'SECOND_PILLAR_LEAVERS' or em.type IS NULL) -- type is null = no email sent yet
        GROUP BY
            ca.current_fund, ca.new_fund, ca.personal_id, ca.first_name, ca.last_name,
            ca.share_amount, ca.share_percentage, ca.date_created,
            fund.ongoing_charges_figure, fund.name_estonian,
            mcmp.email, mcmp.keel, mcmp.vanus;
        """;

    return jdbcClient
        .sql(sql)
        .param("startDate", startDate)
        .param("endDate", endDate)
        .query(AnalyticsLeaver.class)
        .list();
  }
}
