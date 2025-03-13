package ee.tuleva.onboarding.analytics.leavers


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification

import java.time.LocalDate

import static AnalyticsLeaverFixture.leaverFixture

@DataJdbcTest
@Import(AnalyticsLeaversRepository)
class AnalyticsLeaversRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  AnalyticsLeaversRepository analyticsLeaversRepository

  def "can fetch leavers"() {
    given:
    def aLeaver = leaverFixture(null)

    jdbcClient.sql("""
    INSERT INTO analytics.change_application 
      (personal_id, current_fund, new_fund, first_name, last_name, share_amount, share_percentage, reporting_date, date_created)
    VALUES (:personal_id, :current_fund, :new_fund, :first_name, :last_name, :share_amount, :share_percentage, :reporting_date, :date_created)""")
        .param("personal_id", aLeaver.personalCode)
        .param("current_fund", aLeaver.currentFund())
        .param("new_fund", aLeaver.newFund())
        .param("first_name", aLeaver.firstName)
        .param("last_name", aLeaver.lastName)
        .param("share_amount", aLeaver.shareAmount())
        .param("share_percentage", aLeaver.sharePercentage())
        .param("reporting_date", aLeaver.dateCreated())
        .param("date_created", aLeaver.dateCreated())
        .update()

    jdbcClient.sql("""
    INSERT INTO analytics.mv_crm_mailchimp 
    (isikukood, email, keel, vanus)
    VALUES (:isikukood, :email, :keel, :vanus)""")
        .param("isikukood", aLeaver.personalCode)
        .param("email", aLeaver.email())
        .param("keel", aLeaver.language())
        .param("vanus", 30)
        .update()

    when:
    List<AnalyticsLeaver> leavers =
        analyticsLeaversRepository.fetch(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"))


    then:
    leavers == [aLeaver]
  }

  def "can fetch leaver with emails sent"() {
    given:
    def aLeaver = leaverFixture()


    jdbcClient.sql("""
    INSERT INTO analytics.change_application 
      (personal_id, current_fund, new_fund, first_name, last_name, share_amount, share_percentage, reporting_date, date_created)
    VALUES (:personal_id, :current_fund, :new_fund, :first_name, :last_name, :share_amount, :share_percentage, :reporting_date, :date_created)""")
        .param("personal_id", aLeaver.personalCode)
        .param("current_fund", aLeaver.currentFund())
        .param("new_fund", aLeaver.newFund())
        .param("first_name", aLeaver.firstName)
        .param("last_name", aLeaver.lastName)
        .param("share_amount", aLeaver.shareAmount())
        .param("share_percentage", aLeaver.sharePercentage())
        .param("reporting_date", aLeaver.dateCreated())
        .param("date_created", aLeaver.dateCreated())
        .update()

    jdbcClient.sql("""
    INSERT INTO analytics.mv_crm_mailchimp 
    (isikukood, email, keel, vanus)
    VALUES (:isikukood, :email, :keel, :vanus)""")
        .param("isikukood", aLeaver.personalCode)
        .param("email", aLeaver.email())
        .param("keel", aLeaver.language())
        .param("vanus", 30)
        .update()


    jdbcClient.sql("""
    INSERT INTO public.email 
    (personal_code, type, status, created_date)
    VALUES (:personal_code, :type, :status, :created_date)""")
        .param("personal_code", aLeaver.personalCode)
        .param("type", "SECOND_PILLAR_LEAVERS")
        .param("status", "SCHEDULED")
        .param("created_date", aLeaver.lastEmailSentDate())
        .update()

    when:
    List<AnalyticsLeaver> leavers =
        analyticsLeaversRepository.fetch(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"))


    then:
    leavers == [aLeaver]
  }
}
