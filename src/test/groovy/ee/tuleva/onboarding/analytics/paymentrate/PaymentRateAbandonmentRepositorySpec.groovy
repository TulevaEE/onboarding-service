package ee.tuleva.onboarding.analytics.paymentrate

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Requires
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonmentFixture.aPaymentRateAbandonment
import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonmentFixture.uniqueEmail
import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonmentFixture.uniquePersonalCode

@Requires({ System.getenv('CI') == 'true' || System.getenv('SPRING_PROFILES_ACTIVE')?.contains('ci') })
@DataJdbcTest
@Import(PaymentRateAbandonmentRepository)
class PaymentRateAbandonmentRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  PaymentRateAbandonmentRepository repository

  def "fetches payment rate abandonments for users who viewed the page"() {
    given:
    def abandonment = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(1))
        .email(uniqueEmail(1))
        .count(5)
        .currentRate(2)
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)
    def eventTimestamp = LocalDateTime.of(2025, 1, 15, 10, 0)

    insertEventLog(abandonment, eventTimestamp, 5)
    insertUnitOwner(abandonment, snapshotDate)
    insertEmail(abandonment)

    when:
    def result = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    result == [abandonment]
  }

  def "includes users who have never been emailed"() {
    given:
    def abandonment = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(2))
        .email(uniqueEmail(2))
        .count(3)
        .currentRate(4)
        .lastEmailSentDate(null)
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)
    def eventTimestamp = LocalDateTime.of(2025, 1, 20, 14, 30)

    insertEventLog(abandonment, eventTimestamp, 3)
    insertUnitOwner(abandonment, snapshotDate)

    when:
    def result = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    result == [abandonment]
    result.first().lastEmailSentDate() == null
  }

  def "returns only rows from the latest snapshot"() {
    given:
    def newest = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(3))
        .email(uniqueEmail(3))
        .count(2)
        .build()

    def older = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(4))
        .email(uniqueEmail(4))
        .count(1)
        .build()

    def newestSnapshotDate = LocalDate.of(2025, 1, 31)
    def olderSnapshotDate = LocalDate.of(2025, 1, 15)
    def eventTimestamp = LocalDateTime.of(2025, 1, 20, 10, 0)

    insertEventLog(newest, eventTimestamp, 2)
    insertUnitOwner(newest, newestSnapshotDate)
    insertEmail(newest)

    insertEventLog(older, eventTimestamp, 1)
    insertUnitOwner(older, olderSnapshotDate)
    insertEmail(older)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results == [newest]
  }

  def "only fetches users with current rate 2 or 4"() {
    given:
    def rate2User = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(5))
        .email(uniqueEmail(5))
        .count(1)
        .currentRate(2)
        .build()

    def rate4User = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(6))
        .email(uniqueEmail(6))
        .count(2)
        .currentRate(4)
        .build()

    def rate6User = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(7))
        .email(uniqueEmail(7))
        .count(1)
        .currentRate(6)
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)
    def eventTimestamp = LocalDateTime.of(2025, 1, 15, 10, 0)

    insertEventLog(rate2User, eventTimestamp, 1)
    insertUnitOwner(rate2User, snapshotDate)

    insertEventLog(rate4User, eventTimestamp, 2)
    insertUnitOwner(rate4User, snapshotDate)

    insertEventLog(rate6User, eventTimestamp, 1)
    insertUnitOwner(rate6User, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 2
    results.collect { it.personalCode() }.sort() == [rate2User.personalCode(), rate4User.personalCode()].sort()
  }

  def "excludes users with pending rate 4 or 6"() {
    given:
    def noPendingRate = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(8))
        .email(uniqueEmail(8))
        .count(1)
        .currentRate(2)
        .build()

    def pendingRate4 = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(9))
        .email(uniqueEmail(9))
        .count(1)
        .currentRate(2)
        .pendingRate(4)
        .pendingRateDate(LocalDate.of(2025, 3, 1))
        .build()

    def pendingRate6 = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(10))
        .email(uniqueEmail(10))
        .count(1)
        .currentRate(4)
        .pendingRate(6)
        .pendingRateDate(LocalDate.of(2025, 3, 1))
        .build()

    def pendingRate2 = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(11))
        .email(uniqueEmail(11))
        .count(1)
        .currentRate(2)
        .pendingRate(2)
        .pendingRateDate(LocalDate.of(2025, 3, 1))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)
    def eventTimestamp = LocalDateTime.of(2025, 1, 15, 10, 0)

    insertEventLog(noPendingRate, eventTimestamp, 1)
    insertUnitOwner(noPendingRate, snapshotDate)

    insertEventLog(pendingRate4, eventTimestamp, 1)
    insertUnitOwner(pendingRate4, snapshotDate)

    insertEventLog(pendingRate6, eventTimestamp, 1)
    insertUnitOwner(pendingRate6, snapshotDate)

    insertEventLog(pendingRate2, eventTimestamp, 1)
    insertUnitOwner(pendingRate2, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 2
    results.collect { it.personalCode() }.sort() == [noPendingRate.personalCode(), pendingRate2.personalCode()].sort()
  }

  def "filters events by date range"() {
    given:
    def inRange = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(12))
        .email(uniqueEmail(12))
        .count(1)
        .build()

    def beforeRange = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(13))
        .email(uniqueEmail(13))
        .count(1)
        .build()

    def afterRange = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(14))
        .email(uniqueEmail(14))
        .count(1)
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(inRange, LocalDateTime.of(2025, 1, 15, 10, 0), 1)
    insertUnitOwner(inRange, snapshotDate)

    insertEventLog(beforeRange, LocalDateTime.of(2024, 12, 31, 23, 59), 1)
    insertUnitOwner(beforeRange, snapshotDate)

    insertEventLog(afterRange, LocalDateTime.of(2025, 2, 1, 0, 0), 1)
    insertUnitOwner(afterRange, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == inRange.personalCode()
  }

  def "counts multiple page views per user"() {
    given:
    def multipleViews = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(15))
        .email(uniqueEmail(15))
        .count(7)
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    // Insert 7 page view events for the same user
    7.times {
      insertEventLog(multipleViews, LocalDateTime.of(2025, 1, 15 + it, 10, 0), 1, false)
    }

    insertUnitOwner(multipleViews, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().count() == 7
  }

  private void insertEventLog(PaymentRateAbandonment abandonment,
                               LocalDateTime timestamp,
                               Integer viewCount,
                               boolean insertMultiple = true) {
    if (insertMultiple) {
      viewCount.times {
        jdbcClient.sql("""
            INSERT INTO event_log (principal, type, data, timestamp)
            VALUES (:principal, :type, :data::jsonb, :timestamp)""")
            .param("principal", abandonment.personalCode())
            .param("type", "PAGE_VIEW")
            .param("data", '{"path": "/2nd-pillar-payment-rate"}')
            .param("timestamp", timestamp.plusSeconds(it))
            .update()
      }
    } else {
      jdbcClient.sql("""
            INSERT INTO event_log (principal, type, data, timestamp)
            VALUES (:principal, :type, :data::jsonb, :timestamp)""")
          .param("principal", abandonment.personalCode())
          .param("type", "PAGE_VIEW")
          .param("data", '{"path": "/2nd-pillar-payment-rate"}')
          .param("timestamp", timestamp)
          .update()
    }
  }

  private void insertUnitOwner(PaymentRateAbandonment abandonment, LocalDate snapshotDate) {
    jdbcClient.sql("""
            INSERT INTO unit_owner
              (personal_id, first_name, last_name, email, language_preference,
               p2_rate, p2_next_rate, p2_next_rate_date, snapshot_date, date_created)
            VALUES
              (:personal_id, :first_name, :last_name, :email, :language_preference,
               :p2_rate::INTEGER, :p2_next_rate::INTEGER, :p2_next_rate_date, :snapshot_date, :date_created)""")
        .param("personal_id", abandonment.personalCode())
        .param("first_name", abandonment.firstName())
        .param("last_name", abandonment.lastName())
        .param("email", abandonment.email())
        .param("language_preference", abandonment.language())
        .param("p2_rate", abandonment.currentRate())
        .param("p2_next_rate", abandonment.pendingRate())
        .param("p2_next_rate_date", abandonment.pendingRateDate())
        .param("snapshot_date", snapshotDate)
        .param("date_created", snapshotDate.atStartOfDay())
        .update()
  }

  private void insertEmail(PaymentRateAbandonment abandonment) {
    insertEmail(abandonment, "PAYMENT_RATE_ABANDONMENT", abandonment.lastEmailSentDate())
  }

  private void insertEmail(PaymentRateAbandonment abandonment, String type, LocalDateTime createdDate) {
    if (createdDate != null) {
      jdbcClient.sql("""
            INSERT INTO public.email (personal_code, type, status, created_date)
            VALUES (:personal_code, :type, :status, :created_date)""")
          .param("personal_code", abandonment.personalCode())
          .param("type", type)
          .param("status", "SENT")
          .param("created_date", createdDate)
          .update()
    }
  }
}
