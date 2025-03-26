package ee.tuleva.onboarding.analytics.leavers;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;

import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.auto.AutoEmailRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ExchangeTransactionLeaversRepository
    implements AutoEmailRepository<ExchangeTransactionLeaver> {

  private final JdbcClient jdbcClient;

  @Override
  public List<ExchangeTransactionLeaver> fetch(LocalDate startDate, LocalDate endDate) {
    String sql =
        """
        SELECT
            et.security_from AS "currentFund",
            et.security_to AS "newFund",
            et.code AS "personalCode",
            et.first_name AS "firstName",
            et.name AS "lastName",
            et.unit_amount AS "shareAmount",
            et.percentage AS "sharePercentage",
            et.date_created AS "dateCreated",
            fund.ongoing_charges_figure AS "fundOngoingChargesFigure",
            fund.name_estonian AS "fundNameEstonian",
            mcmp.email AS "email",
            mcmp.keel AS "language",
            MAX(em.created_date) AS "lastEmailSentDate"
        FROM
            public.exchange_transaction et
        LEFT JOIN
            public.fund fund ON et.security_to = fund.short_name
        LEFT JOIN
            analytics.mv_crm_mailchimp mcmp ON et.code = mcmp.isikukood
        LEFT JOIN
            public.email em ON et.code = em.personal_code
        WHERE
            et.date_created >= :startDate AND
            et.date_created < :endDate AND
            (et.security_from = 'TUK75' OR et.security_from = 'TUK00') AND
            et.security_to <> 'TUK00' AND
            et.security_to <> 'TUK75' AND
            fund.ongoing_charges_figure >= 0.005 AND
            mcmp.email IS NOT NULL AND
            (mcmp.keel = 'ENG' OR mcmp.keel = 'EST') AND
            et.percentage >= 10 AND
            (em.type = '%s' OR em.type IS NULL) -- type is null = no email sent yet
        GROUP BY
            et.security_from, et.security_to, et.code, et.first_name, et.name,
            et.unit_amount, et.percentage, et.date_created,
            fund.ongoing_charges_figure, fund.name_estonian,
            mcmp.email, mcmp.keel;
        """
            .formatted(getEmailType());

    return jdbcClient
        .sql(sql)
        .param("startDate", startDate)
        .param("endDate", endDate)
        .query(ExchangeTransactionLeaver.class)
        .list();
  }

  @Override
  public EmailType getEmailType() {
    return SECOND_PILLAR_LEAVERS;
  }
}
