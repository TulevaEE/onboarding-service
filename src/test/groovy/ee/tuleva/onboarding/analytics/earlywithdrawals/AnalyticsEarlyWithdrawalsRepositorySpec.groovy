package ee.tuleva.onboarding.analytics.earlywithdrawals

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalFixture.anEarlyWithdrawal

@DataJdbcTest
@Import(AnalyticsEarlyWithdrawalsRepository)
class AnalyticsEarlyWithdrawalsRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  AnalyticsEarlyWithdrawalsRepository analyticsEarlyWithdrawalsRepository

  def "can fetch withdrawals"() {
    given:
    def aWithdrawal = anEarlyWithdrawal(1)

    jdbcClient.sql("""
    INSERT INTO analytics.tuk75 
      (personal_id, first_name, last_name, email, language, early_withdrawal_date, early_withdrawal_status)
    VALUES (:personal_id, :first_name, :last_name, :email, :language, :early_withdrawal_date, :early_withdrawal_status)""")
        .param("personal_id", aWithdrawal.personalCode())
        .param("first_name", aWithdrawal.firstName())
        .param("last_name", aWithdrawal.lastName())
        .param("email", aWithdrawal.email())
        .param("language", aWithdrawal.language())
        .param("early_withdrawal_date", aWithdrawal.earlyWithdrawalDate())
        .param("early_withdrawal_status", aWithdrawal.earlyWithdrawalStatus())
        .update()

    jdbcClient.sql("""
    INSERT INTO public.email 
    (personal_code, type, status, created_date)
    VALUES (:personal_code, :type, :status, :created_date)""")
        .param("personal_code", aWithdrawal.personalCode())
        .param("type", "SECOND_PILLAR_EARLY_WITHDRAWAL")
        .param("status", "SCHEDULED")
        .param("created_date", aWithdrawal.lastEmailSentDate())
        .update()

    when:
    List<AnalyticsEarlyWithdrawal> withdrawals =
        analyticsEarlyWithdrawalsRepository.fetch(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-03-01"))

    then:
    withdrawals == [aWithdrawal]
  }

  def "can fetch withdrawals from both tables"() {
    given:
    def aWithdrawal = anEarlyWithdrawal(1, null)
    def anotherWithdrawal = anEarlyWithdrawal(2)

    jdbcClient.sql("""
    INSERT INTO analytics.tuk75 
      (personal_id, first_name, last_name, email, language, early_withdrawal_date, early_withdrawal_status)
    VALUES (:personal_id, :first_name, :last_name, :email, :language, :early_withdrawal_date, :early_withdrawal_status)""")
        .param("personal_id", aWithdrawal.personalCode())
        .param("first_name", aWithdrawal.firstName())
        .param("last_name", aWithdrawal.lastName())
        .param("email", aWithdrawal.email())
        .param("language", aWithdrawal.language())
        .param("early_withdrawal_date", aWithdrawal.earlyWithdrawalDate())
        .param("early_withdrawal_status", aWithdrawal.earlyWithdrawalStatus())
        .update()

    jdbcClient.sql("""
    INSERT INTO analytics.tuk00 
      (personal_id, first_name, last_name, email, language, early_withdrawal_date, early_withdrawal_status)
    VALUES (:personal_id, :first_name, :last_name, :email, :language, :early_withdrawal_date, :early_withdrawal_status)""")
        .param("personal_id", anotherWithdrawal.personalCode())
        .param("first_name", anotherWithdrawal.firstName())
        .param("last_name", anotherWithdrawal.lastName())
        .param("email", anotherWithdrawal.email())
        .param("language", anotherWithdrawal.language())
        .param("early_withdrawal_date", anotherWithdrawal.earlyWithdrawalDate())
        .param("early_withdrawal_status", anotherWithdrawal.earlyWithdrawalStatus())
        .update()

    jdbcClient.sql("""
    INSERT INTO public.email 
    (personal_code, type, status, created_date)
    VALUES (:personal_code, :type, :status, :created_date)""")
        .param("personal_code", anotherWithdrawal.personalCode())
        .param("type", "SECOND_PILLAR_EARLY_WITHDRAWAL")
        .param("status", "SCHEDULED")
        .param("created_date", anotherWithdrawal.lastEmailSentDate())
        .update()

    when:
    List<AnalyticsEarlyWithdrawal> withdrawals =
        analyticsEarlyWithdrawalsRepository.fetch(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-03-01"))

    then:
    withdrawals == [aWithdrawal, anotherWithdrawal]
  }
}
