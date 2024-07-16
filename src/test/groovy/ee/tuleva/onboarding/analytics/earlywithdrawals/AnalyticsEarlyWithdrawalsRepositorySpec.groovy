package ee.tuleva.onboarding.analytics.earlywithdrawals

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification

import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalFixture.anEarlyWithdrawal
import static ee.tuleva.onboarding.analytics.earlywithdrawals.AnalyticsEarlyWithdrawalFixture.anEarlyWithdrawalWithMissingEmail

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

    insertStockFund(aWithdrawal)
    insertEmail(aWithdrawal)

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

    insertStockFund(aWithdrawal)
    insertBondFund(anotherWithdrawal)
    insertEmail(anotherWithdrawal)

    when:
    List<AnalyticsEarlyWithdrawal> withdrawals =
        analyticsEarlyWithdrawalsRepository.fetch(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-03-01"))

    then:
    withdrawals == [aWithdrawal, anotherWithdrawal]
  }

  def "ignores missing emails"() {
    given:
    def aWithdrawal = anEarlyWithdrawalWithMissingEmail(3)
    def anotherWithdrawal = anEarlyWithdrawalWithMissingEmail(4)

    insertStockFund(aWithdrawal)
    insertBondFund(anotherWithdrawal)

    when:
    List<AnalyticsEarlyWithdrawal> withdrawals =
        analyticsEarlyWithdrawalsRepository.fetch(LocalDate.parse("2023-02-01"), LocalDate.parse("2023-03-01"))

    then:
    withdrawals == []
  }

  private void insertBondFund(AnalyticsEarlyWithdrawal withdrawal) {
    jdbcClient.sql("""
    INSERT INTO analytics.tuk00 
      (personal_id, first_name, last_name, email, language, early_withdrawal_date, early_withdrawal_status)
    VALUES (:personal_id, :first_name, :last_name, :email, :language, :early_withdrawal_date, :early_withdrawal_status)""")
        .param("personal_id", withdrawal.personalCode())
        .param("first_name", withdrawal.firstName())
        .param("last_name", withdrawal.lastName())
        .param("email", withdrawal.email())
        .param("language", withdrawal.language())
        .param("early_withdrawal_date", withdrawal.earlyWithdrawalDate())
        .param("early_withdrawal_status", withdrawal.earlyWithdrawalStatus())
        .update()
  }

  private void insertStockFund(AnalyticsEarlyWithdrawal withdrawal) {
    jdbcClient.sql("""
    INSERT INTO analytics.tuk75 
      (personal_id, first_name, last_name, email, language, early_withdrawal_date, early_withdrawal_status)
    VALUES (:personal_id, :first_name, :last_name, :email, :language, :early_withdrawal_date, :early_withdrawal_status)""")
        .param("personal_id", withdrawal.personalCode())
        .param("first_name", withdrawal.firstName())
        .param("last_name", withdrawal.lastName())
        .param("email", withdrawal.email())
        .param("language", withdrawal.language())
        .param("early_withdrawal_date", withdrawal.earlyWithdrawalDate())
        .param("early_withdrawal_status", withdrawal.earlyWithdrawalStatus())
        .update()
  }

  private void insertEmail(AnalyticsEarlyWithdrawal withdrawal) {
    jdbcClient.sql("""
    INSERT INTO public.email 
    (personal_code, type, status, created_date)
    VALUES (:personal_code, :type, :status, :created_date)""")
        .param("personal_code", withdrawal.personalCode())
        .param("type", "SECOND_PILLAR_EARLY_WITHDRAWAL")
        .param("status", "SCHEDULED")
        .param("created_date", withdrawal.lastEmailSentDate())
        .update()
  }
}
