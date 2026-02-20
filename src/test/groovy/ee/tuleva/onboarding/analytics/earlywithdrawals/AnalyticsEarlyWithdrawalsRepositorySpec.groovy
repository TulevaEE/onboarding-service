package ee.tuleva.onboarding.analytics.earlywithdrawals

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest
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
  AnalyticsEarlyWithdrawalsRepository repository

  def "fetches withdrawals when filtered by choice application"() {
    given:
    def withdrawal = anEarlyWithdrawal(1, "A")
    insertUnitOwner(1001L, withdrawal, "TUK75", LocalDate.of(2023, 3, 31))
    insertEmail(withdrawal)

    when:
    def result = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    result == [withdrawal]
  }

  def "fetches withdrawals when matched via existing balance"() {
    given:
    def withdrawal = anEarlyWithdrawal(2, "A")
    long ownerId = insertUnitOwner(1002L, withdrawal, "Fond", LocalDate.of(2023, 3, 31))
    insertUnitOwnerBalance(ownerId, "TUK00")
    insertEmail(withdrawal)

    when:
    def result = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    result == [withdrawal]
  }

  def "returns only rows from the latest snapshot"() {
    given:
    def newest = anEarlyWithdrawal(3, "A")
    def older = anEarlyWithdrawal(4, "A")

    insertUnitOwner(1003L, newest, "TUK75", LocalDate.of(2023, 3, 31))
    insertEmail(newest)

    insertUnitOwner(1004L, older, "TUK75", LocalDate.of(2023, 2, 28))
    insertEmail(older)

    when:
    def results = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    results == [newest]
  }

  def "includes unit owners that have never been e‑mailed"() {
    given:
    def withdrawal = anEarlyWithdrawal(5, "A", null)
    insertUnitOwner(1005L, withdrawal, "TUK00", LocalDate.of(2023, 3, 31))

    when:
    def results = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    results == [withdrawal]
    results.first().lastEmailSentDate() == null
  }

  def "ignores unit owners with a blank e‑mail address"() {
    given:
    def withdrawal = anEarlyWithdrawalWithMissingEmail(6)
    insertUnitOwner(1006L, withdrawal, "TUK00", LocalDate.of(2023, 3, 31))

    when:
    def results = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    results == []
  }

  def "does not ignore owners whose previous e‑mail was of a different type"() {
    given:
    def withdrawalWithEmailOfDifferentType = anEarlyWithdrawal(7, "A")
    def expectedWithdrawal = anEarlyWithdrawal(7, "A", null)
    insertUnitOwner(1007L, withdrawalWithEmailOfDifferentType, "TUK75", LocalDate.of(2023, 3, 31))
    insertEmail(withdrawalWithEmailOfDifferentType, "SOME_OTHER_TYPE")

    when:
    def result = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    result == [expectedWithdrawal]
  }

  def "ignores unit owners with p2_rava_status not equal to 'A'"() {
    given:
    def withdrawal = anEarlyWithdrawal(8, "P")
    insertUnitOwner(1008L, withdrawal, "TUK75", LocalDate.of(2023, 3, 31))

    when:
    def result = repository.fetch(LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"))

    then:
    result == []
  }

  private long insertUnitOwner(long ownerId,
                               AnalyticsEarlyWithdrawal withdrawal,
                               String choiceApplication,
                               LocalDate snapshotDate) {
    jdbcClient.sql("""
            INSERT INTO unit_owner
              (id, personal_id, first_name, last_name, email, language_preference,
               p2_choice, p2_rava_date, p2_rava_status, snapshot_date, date_created)
            VALUES
              (:id, :personal_id, :first_name, :last_name, :email, :language_preference,
               :p2_choice, :p2_rava_date, :p2_rava_status, :snapshot_date, :date_created)""")
        .param("id", ownerId)
        .param("personal_id", withdrawal.personalCode())
        .param("first_name", withdrawal.firstName())
        .param("last_name", withdrawal.lastName())
        .param("email", withdrawal.email())
        .param("language_preference", withdrawal.language())
        .param("p2_choice", choiceApplication)
        .param("p2_rava_date", withdrawal.earlyWithdrawalDate())
        .param("p2_rava_status", withdrawal.earlyWithdrawalStatus())
        .param("snapshot_date", snapshotDate)
        .param("date_created", snapshotDate)
        .update()
    return ownerId
  }

  private void insertUnitOwnerBalance(long unitOwnerId, String shortName) {
    jdbcClient.sql("""
            INSERT INTO unit_owner_balance (unit_owner_id, security_short_name)
            VALUES (:unit_owner_id, :security_short_name)""")
        .param("unit_owner_id", unitOwnerId)
        .param("security_short_name", shortName)
        .update()
  }

  private void insertEmail(AnalyticsEarlyWithdrawal withdrawal) {
    insertEmail(withdrawal, "SECOND_PILLAR_EARLY_WITHDRAWAL")
  }

  private void insertEmail(AnalyticsEarlyWithdrawal withdrawal, String type) {
    jdbcClient.sql("""
            INSERT INTO public.email (personal_code, type, status, created_date)
            VALUES (:personal_code, :type, :status, :created_date)""")
        .param("personal_code", withdrawal.personalCode())
        .param("type", type)
        .param("status", "SENT")
        .param("created_date", withdrawal.lastEmailSentDate())
        .update()
  }
}
