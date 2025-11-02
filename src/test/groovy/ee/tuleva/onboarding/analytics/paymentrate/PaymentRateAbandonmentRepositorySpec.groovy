package ee.tuleva.onboarding.analytics.paymentrate

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Requires
import spock.lang.Specification

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonmentFixture.aPaymentRateAbandonment
import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonmentFixture.uniqueEmail
import static ee.tuleva.onboarding.analytics.paymentrate.PaymentRateAbandonmentFixture.uniquePersonalCode

@Requires({ System.getenv('SPRING_PROFILES_ACTIVE')?.contains('ci') })
@DataJdbcTest
@Import(PaymentRateAbandonmentRepository)
class PaymentRateAbandonmentRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  PaymentRateAbandonmentRepository repository

  def "fetches payment rate abandonments for users who viewed the page"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    def abandonment = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(1))
        .email(uniqueEmail(1))
        .count(5)
        .currentRate(2)
        .timestamp(Instant.parse("2025-01-15T10:00:04Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    5.times { insertEventLog(abandonment, eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(abandonment, snapshotDate)
    insertEmail(abandonment)

    when:
    def result = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    result == [abandonment]
  }

  def "includes users who have never been emailed"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-20T14:30:00Z")
    def abandonment = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(2))
        .email(uniqueEmail(2))
        .count(3)
        .currentRate(4)
        .lastEmailSentDate(null)
        .timestamp(Instant.parse("2025-01-20T14:30:02Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    3.times { insertEventLog(abandonment, eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(abandonment, snapshotDate)

    when:
    def result = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    result == [abandonment]
    result.first().lastEmailSentDate() == null
  }

  def "returns only rows from the latest snapshot"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-20T10:00:00Z")
    def newest = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(3))
        .email(uniqueEmail(3))
        .count(2)
        .timestamp(Instant.parse("2025-01-20T10:00:01Z"))
        .build()

    def older = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(4))
        .email(uniqueEmail(4))
        .count(1)
        .timestamp(Instant.parse("2025-01-20T10:00:00Z"))
        .build()

    def newestSnapshotDate = LocalDate.of(2025, 1, 31)
    def olderSnapshotDate = LocalDate.of(2025, 1, 15)

    2.times { insertEventLog(newest, eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(newest, newestSnapshotDate)
    insertEmail(newest)

    insertEventLog(older, eventTimestamp)
    insertUnitOwner(older, olderSnapshotDate)
    insertEmail(older)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results == [newest]
  }

  def "only fetches users with current rate 2 or 4"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    def rate2User = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(5))
        .email(uniqueEmail(5))
        .count(1)
        .currentRate(2)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def rate4User = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(6))
        .email(uniqueEmail(6))
        .count(2)
        .currentRate(4)
        .timestamp(Instant.parse("2025-01-15T10:00:01Z"))
        .build()

    def rate6User = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(7))
        .email(uniqueEmail(7))
        .count(1)
        .currentRate(6)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(rate2User, eventTimestamp)
    insertUnitOwner(rate2User, snapshotDate)

    2.times { insertEventLog(rate4User, eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(rate4User, snapshotDate)

    insertEventLog(rate6User, eventTimestamp)
    insertUnitOwner(rate6User, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 2
    results.collect { it.personalCode() }.sort() == [rate2User.personalCode(), rate4User.personalCode()].sort()
  }

  def "excludes users with pending rate 4 or 6"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    def noPendingRate = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(8))
        .email(uniqueEmail(8))
        .count(1)
        .currentRate(2)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def pendingRate4 = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(9))
        .email(uniqueEmail(9))
        .count(1)
        .currentRate(2)
        .pendingRate(4)
        .pendingRateDate(LocalDate.of(2025, 3, 1))
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def pendingRate6 = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(10))
        .email(uniqueEmail(10))
        .count(1)
        .currentRate(4)
        .pendingRate(6)
        .pendingRateDate(LocalDate.of(2025, 3, 1))
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def pendingRate2 = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(11))
        .email(uniqueEmail(11))
        .count(1)
        .currentRate(2)
        .pendingRate(2)
        .pendingRateDate(LocalDate.of(2025, 3, 1))
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(noPendingRate, eventTimestamp)
    insertUnitOwner(noPendingRate, snapshotDate)

    insertEventLog(pendingRate4, eventTimestamp)
    insertUnitOwner(pendingRate4, snapshotDate)

    insertEventLog(pendingRate6, eventTimestamp)
    insertUnitOwner(pendingRate6, snapshotDate)

    insertEventLog(pendingRate2, eventTimestamp)
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
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def beforeRange = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(13))
        .email(uniqueEmail(13))
        .count(1)
        .timestamp(Instant.parse("2024-12-31T23:59:00Z"))
        .build()

    def afterRange = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(14))
        .email(uniqueEmail(14))
        .count(1)
        .timestamp(Instant.parse("2025-02-01T00:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(inRange, Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(inRange, snapshotDate)

    insertEventLog(beforeRange, Instant.parse("2024-12-31T23:59:00Z"))
    insertUnitOwner(beforeRange, snapshotDate)

    insertEventLog(afterRange, Instant.parse("2025-02-01T00:00:00Z"))
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
        .timestamp(Instant.parse("2025-01-21T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    7.times {
      insertEventLog(multipleViews, Instant.parse("2025-01-${15 + it}T10:00:00Z"))
    }

    insertUnitOwner(multipleViews, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().count() == 7
  }

  def "excludes users who viewed after date_created"() {
    given:
    def viewedBeforeCreated = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(16))
        .email(uniqueEmail(16))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def viewedAfterCreated = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(17))
        .email(uniqueEmail(17))
        .count(1)
        .timestamp(Instant.parse("2025-02-01T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(viewedBeforeCreated, Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(viewedBeforeCreated, snapshotDate)

    insertEventLog(viewedAfterCreated, Instant.parse("2025-02-01T10:00:00Z"))
    insertUnitOwner(viewedAfterCreated, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == viewedBeforeCreated.personalCode()
  }

  def "excludes events from December"() {
    given:
    def novemberEvent = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(18))
        .email(uniqueEmail(18))
        .count(1)
        .timestamp(Instant.parse("2024-11-15T10:00:00Z"))
        .build()

    def decemberEvent = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(19))
        .email(uniqueEmail(19))
        .count(1)
        .timestamp(Instant.parse("2024-12-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2024, 11, 30)

    insertEventLog(novemberEvent, Instant.parse("2024-11-15T10:00:00Z"))
    insertUnitOwner(novemberEvent, snapshotDate)

    insertEventLog(decemberEvent, Instant.parse("2024-12-15T10:00:00Z"))
    insertUnitOwner(decemberEvent, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2024-11-01"), LocalDate.parse("2024-12-31"))

    then:
    results.size() == 1
    results.first().personalCode() == novemberEvent.personalCode()
  }

  def "uses latest non-December snapshot"() {
    given:
    def abandonment = aPaymentRateAbandonment()
        .personalCode(uniquePersonalCode(20))
        .email(uniqueEmail(20))
        .count(1)
        .timestamp(Instant.parse("2024-11-15T10:00:00Z"))
        .build()

    def novemberSnapshotDate = LocalDate.of(2024, 11, 30)
    def decemberSnapshotDate = LocalDate.of(2024, 12, 31)

    insertEventLog(abandonment, Instant.parse("2024-11-15T10:00:00Z"))

    // Insert older November snapshot
    insertUnitOwner(abandonment, novemberSnapshotDate)

    // Insert newer December snapshot with different data
    def decemberAbandonment = aPaymentRateAbandonment()
        .personalCode(abandonment.personalCode())
        .email(abandonment.email())
        .firstName("UpdatedFirstName")
        .lastName("UpdatedLastName")
        .count(1)
        .timestamp(abandonment.timestamp())
        .currentRate(4)  // Different rate in December snapshot
        .build()
    insertUnitOwner(decemberAbandonment, decemberSnapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2024-11-01"), LocalDate.parse("2024-12-31"))

    then:
    results.size() == 1
    // Should use November snapshot data, not December
    results.first().currentRate() == 2
    results.first().firstName() == abandonment.firstName()
  }

  private void insertEventLog(PaymentRateAbandonment abandonment, Instant timestamp) {
    jdbcClient.sql("""
            INSERT INTO event_log (principal, type, data, timestamp)
            VALUES (:principal, :type, :data::jsonb, :timestamp)""")
        .param("principal", abandonment.personalCode())
        .param("type", "PAGE_VIEW")
        .param("data", '{"path": "/2nd-pillar-payment-rate"}')
        .param("timestamp", Timestamp.from(timestamp))
        .update()
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

  private void insertEmail(PaymentRateAbandonment abandonment, String type, Instant createdDate) {
    if (createdDate != null) {
      jdbcClient.sql("""
            INSERT INTO email (personal_code, type, status, created_date)
            VALUES (:personal_code, :type, :status, :created_date)""")
          .param("personal_code", abandonment.personalCode())
          .param("type", type)
          .param("status", "SENT")
          .param("created_date", Timestamp.from(createdDate))
          .update()
    }
  }
}
