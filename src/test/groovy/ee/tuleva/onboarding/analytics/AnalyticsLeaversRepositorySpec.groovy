package ee.tuleva.onboarding.analytics

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification

import java.time.LocalDate

@DataJdbcTest
@Import(AnalyticsLeaversRepository)
class AnalyticsLeaversRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  AnalyticsLeaversRepository analyticsLeaversRepository

  def "can fetch leavers"() {
    given:
    jdbcClient.sql("""
    INSERT INTO analytics.change_application 
      (personal_id, current_fund, new_fund, first_name, last_name, share_amount, share_percentage, reporting_date, date_created)
    VALUES (:personal_id, :current_fund, :new_fund, :first_name, :last_name, :share_amount, :share_percentage, :reporting_date, :date_created)""")
        .param("personal_id", "123456789")
        .param("current_fund", "TUK75")
        .param("new_fund", "LXK75")
        .param("first_name", "John")
        .param("last_name", "Doe")
        .param("share_amount", 1000)
        .param("share_percentage", 100)
        .param("reporting_date", "2021-01-01")
        .param("date_created", "2021-01-01")
        .update()

    jdbcClient.sql("""
    INSERT INTO analytics.mv_crm_mailchimp 
    (isikukood, email, keel, vanus)
    VALUES (:isikukood, :email, :keel, :vanus)""")
        .param("isikukood", "123456789")
        .param("email", "john@doe.com")
        .param("keel", "ENG")
        .param("vanus", 30)
        .update()

    when:
    List<AnalyticsLeaver> leavers =
        analyticsLeaversRepository.fetchLeavers(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"))

    then:
    leavers == [
        new AnalyticsLeaver(
            "TUK75",
            "LXK75",
            "123456789",
            "John",
            "Doe",
            1000,
            100,
            LocalDate.parse("2021-01-01"),
            0.0113,
            "LHV Pensionifond XL",
            "john@doe.com",
            "ENG",
            30
        )
    ]
  }
}
