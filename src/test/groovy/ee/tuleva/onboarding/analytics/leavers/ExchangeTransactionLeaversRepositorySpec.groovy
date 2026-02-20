package ee.tuleva.onboarding.analytics.leavers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.aLeaverWith
import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.anotherLeaverWith
import static ee.tuleva.onboarding.analytics.leavers.ExchangeTransactionLeaverFixture.thirdLeaverWith


@DataJdbcTest
@Import(ExchangeTransactionLeaversRepository)
class ExchangeTransactionLeaversRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  ExchangeTransactionLeaversRepository leaversRepository

  def "can fetch leavers"() {
    given:
    def aLeaver = aLeaverWith()
        .lastEmailSentDate(null)
        .build()
    insertIntoExchangeTransaction(aLeaver)
    insertIntoMvCrmMailchimp(aLeaver)

    when:
    List<ExchangeTransactionLeaver> leavers =
        leaversRepository.fetch(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"))


    then:
    leavers == [aLeaver]
  }

  def "can fetch leaver with emails sent"() {
    given:
    def aLeaver = aLeaverWith().build()
    insertIntoExchangeTransaction(aLeaver)
    insertIntoMvCrmMailchimp(aLeaver)
    insertIntoEmail(aLeaver)

    when:
    List<ExchangeTransactionLeaver> leavers =
        leaversRepository.fetch(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"))


    then:
    leavers == [aLeaver]
  }

  def "does not fetch old reporting date leavers"() {
    given:
    def aLeaver = aLeaverWith()
        .lastEmailSentDate(null)
        .build()
    def aLeaver2 = anotherLeaverWith()
        .lastEmailSentDate(null)
        .build()
    def aLeaver3 = thirdLeaverWith()
        .lastEmailSentDate(null)
        .build()

    def oldReportingDate = LocalDate.parse("2016-01-01")
    def previousReportingDate = LocalDate.parse("2021-01-01")
    def currentReportingDate = LocalDate.parse("2021-02-01")
    insertIntoExchangeTransaction(aLeaver, oldReportingDate)
    insertIntoExchangeTransaction(aLeaver2, previousReportingDate)
    insertIntoExchangeTransaction(aLeaver3, currentReportingDate)

    insertIntoMvCrmMailchimp(aLeaver)
    insertIntoMvCrmMailchimp(aLeaver2)
    insertIntoMvCrmMailchimp(aLeaver3)

    when:
    List<ExchangeTransactionLeaver> leavers =
        leaversRepository.fetch(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"))

    then:
    leavers == [aLeaver3]
  }

  def "includes transactions created on last day when end date is inclusive"() {
    given:
    def today = LocalDate.parse("2021-01-31")
    def tomorrow = today.plusDays(1)
    def reportingDate = tomorrow

    def leaverCreatedToday = aLeaverWith()
        .dateCreated(today)
        .lastEmailSentDate(null)
        .build()

    def leaverCreatedTomorrow = anotherLeaverWith()
        .dateCreated(tomorrow)
        .lastEmailSentDate(null)
        .build()

    insertIntoExchangeTransaction(leaverCreatedToday, reportingDate)
    insertIntoExchangeTransaction(leaverCreatedTomorrow, reportingDate)

    insertIntoMvCrmMailchimp(leaverCreatedToday)
    insertIntoMvCrmMailchimp(leaverCreatedTomorrow)

    when:
    List<ExchangeTransactionLeaver> leavers =
        leaversRepository.fetch(LocalDate.parse("2021-01-01"), today)

    then:
    leavers == [leaverCreatedToday]
    !leavers.contains(leaverCreatedTomorrow)
  }

  def "excludes transactions created before first day when start date is inclusive"() {
    given:
    def today = LocalDate.parse("2021-01-01")
    def yesterday = today.minusDays(1)
    def reportingDate = today.plusMonths(1)

    def leaverCreatedYesterday = aLeaverWith()
        .dateCreated(yesterday)
        .lastEmailSentDate(null)
        .build()

    def leaverCreatedToday = anotherLeaverWith()
        .dateCreated(today)
        .lastEmailSentDate(null)
        .build()

    insertIntoExchangeTransaction(leaverCreatedYesterday, reportingDate)
    insertIntoExchangeTransaction(leaverCreatedToday, reportingDate)

    insertIntoMvCrmMailchimp(leaverCreatedYesterday)
    insertIntoMvCrmMailchimp(leaverCreatedToday)

    when:
    List<ExchangeTransactionLeaver> leavers =
        leaversRepository.fetch(today, LocalDate.parse("2021-01-31"))

    then:
    leavers == [leaverCreatedToday]
    !leavers.contains(leaverCreatedYesterday)
  }

  private def insertIntoExchangeTransaction(ExchangeTransactionLeaver leaver,
                                            LocalDate reportingDate = leaver.dateCreated()) {
    jdbcClient.sql("""
    INSERT INTO public.exchange_transaction 
      (code, security_from, security_to, first_name, name, unit_amount, percentage, reporting_date, date_created)
    VALUES (:code, :security_from, :security_to, :first_name, :name, :unit_amount, :percentage, :reporting_date, :date_created)""")
        .param("code", leaver.personalCode)
        .param("security_from", leaver.currentFund())
        .param("security_to", leaver.newFund())
        .param("first_name", leaver.firstName)
        .param("name", leaver.lastName)
        .param("unit_amount", leaver.shareAmount())
        .param("percentage", leaver.sharePercentage())
        .param("reporting_date", reportingDate)
        .param("date_created", leaver.dateCreated())
        .update()
  }

  private def insertIntoMvCrmMailchimp(ExchangeTransactionLeaver leaver) {
    jdbcClient.sql("""
    INSERT INTO analytics.mv_crm_mailchimp 
    (isikukood, email, keel, vanus)
    VALUES (:isikukood, :email, :keel, :vanus)""")
        .param("isikukood", leaver.personalCode)
        .param("email", leaver.email())
        .param("keel", leaver.language())
        .param("vanus", 30)
        .update()
  }

  private def insertIntoEmail(ExchangeTransactionLeaver aLeaver) {
    jdbcClient.sql("""
    INSERT INTO public.email
    (personal_code, type, status, created_date)
    VALUES (:personal_code, :type, :status, :created_date)""")
        .param("personal_code", aLeaver.personalCode)
        .param("type", "SECOND_PILLAR_LEAVERS")
        .param("status", "SCHEDULED")
        .param("created_date", aLeaver.lastEmailSentDate())
        .update()
  }
}
