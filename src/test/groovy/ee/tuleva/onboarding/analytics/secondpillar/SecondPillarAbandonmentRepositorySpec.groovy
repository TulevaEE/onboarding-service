package ee.tuleva.onboarding.analytics.secondpillar

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Requires
import spock.lang.Specification

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.analytics.secondpillar.SecondPillarAbandonmentFixture.*

@Requires({ System.getenv('SPRING_PROFILES_ACTIVE')?.contains('ci') })
@DataJdbcTest
@Import(SecondPillarAbandonmentRepository)
class SecondPillarAbandonmentRepositorySpec extends Specification {

  @Autowired
  JdbcClient jdbcClient

  @Autowired
  SecondPillarAbandonmentRepository repository

  def "fetches second pillar abandonments for users who viewed the flow page"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    def abandonment = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(1))
        .email(uniqueEmail(1))
        .count(5)
        .currentRate(2)
        .timestamp(Instant.parse("2025-01-15T10:00:04Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    5.times { insertEventLog(abandonment, "/2nd-pillar-flow", eventTimestamp.plusSeconds(it)) }
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
    def abandonment = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(2))
        .email(uniqueEmail(2))
        .count(3)
        .currentRate(4)
        .lastEmailSentDate(null)
        .timestamp(Instant.parse("2025-01-20T14:30:02Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    3.times { insertEventLog(abandonment, "/2nd-pillar-flow", eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(abandonment, snapshotDate)

    when:
    def result = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    result == [abandonment]
    result.first().lastEmailSentDate() == null
  }

  def "includes users who viewed partner flow"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    def abandonment = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(3))
        .email(uniqueEmail(3))
        .count(2)
        .path("/2nd-pillar-flow")
        .timestamp(Instant.parse("2025-01-15T10:00:01Z"))
        .build()

    def partnerAbandonment = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(4))
        .email(uniqueEmail(4))
        .count(1)
        .path("/partner/2nd-pillar-flow")
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    2.times { insertEventLog(abandonment, "/2nd-pillar-flow", eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(abandonment, snapshotDate)

    insertEventLog(partnerAbandonment, "/partner/2nd-pillar-flow", eventTimestamp)
    insertUnitOwner(partnerAbandonment, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 2
    results.collect { it.personalCode() }.sort() == [abandonment.personalCode(), partnerAbandonment.personalCode()].sort()
  }

  def "excludes users who viewed success page"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-15T10:00:00Z")
    def abandonment = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(5))
        .email(uniqueEmail(5))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def successPageUser = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(6))
        .email(uniqueEmail(6))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(abandonment, "/2nd-pillar-flow", eventTimestamp)
    insertUnitOwner(abandonment, snapshotDate)

    insertEventLog(successPageUser, "/2nd-pillar-flow/success", eventTimestamp)
    insertUnitOwner(successPageUser, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == abandonment.personalCode()
  }

  def "returns only rows from the latest snapshot"() {
    given:
    def eventTimestamp = Instant.parse("2025-01-20T10:00:00Z")
    def newest = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(7))
        .email(uniqueEmail(7))
        .count(2)
        .timestamp(Instant.parse("2025-01-20T10:00:01Z"))
        .build()

    def older = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(8))
        .email(uniqueEmail(8))
        .count(1)
        .timestamp(Instant.parse("2025-01-20T10:00:00Z"))
        .build()

    def newestSnapshotDate = LocalDate.of(2025, 1, 31)
    def olderSnapshotDate = LocalDate.of(2025, 1, 15)

    2.times { insertEventLog(newest, "/2nd-pillar-flow", eventTimestamp.plusSeconds(it)) }
    insertUnitOwner(newest, newestSnapshotDate)
    insertEmail(newest)

    insertEventLog(older, "/2nd-pillar-flow", eventTimestamp)
    insertUnitOwner(older, olderSnapshotDate)
    insertEmail(older)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results == [newest]
  }

  def "filters events by date range"() {
    given:
    def inRange = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(9))
        .email(uniqueEmail(9))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def beforeRange = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(10))
        .email(uniqueEmail(10))
        .count(1)
        .timestamp(Instant.parse("2024-12-31T23:59:00Z"))
        .build()

    def afterRange = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(11))
        .email(uniqueEmail(11))
        .count(1)
        .timestamp(Instant.parse("2025-02-01T00:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(inRange, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(inRange, snapshotDate)

    insertEventLog(beforeRange, "/2nd-pillar-flow", Instant.parse("2024-12-31T23:59:00Z"))
    insertUnitOwner(beforeRange, snapshotDate)

    insertEventLog(afterRange, "/2nd-pillar-flow", Instant.parse("2025-02-01T00:00:00Z"))
    insertUnitOwner(afterRange, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == inRange.personalCode()
  }

  def "counts multiple page views per user"() {
    given:
    def multipleViews = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(12))
        .email(uniqueEmail(12))
        .count(7)
        .timestamp(Instant.parse("2025-01-21T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    7.times {
      insertEventLog(multipleViews, "/2nd-pillar-flow", Instant.parse("2025-01-${15 + it}T10:00:00Z"))
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
    def viewedBeforeCreated = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(13))
        .email(uniqueEmail(13))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def viewedAfterCreated = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(14))
        .email(uniqueEmail(14))
        .count(1)
        .timestamp(Instant.parse("2025-02-01T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(viewedBeforeCreated, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(viewedBeforeCreated, snapshotDate)

    insertEventLog(viewedAfterCreated, "/2nd-pillar-flow", Instant.parse("2025-02-01T10:00:00Z"))
    insertUnitOwner(viewedAfterCreated, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == viewedBeforeCreated.personalCode()
  }

  def "excludes events from December"() {
    given:
    def novemberEvent = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(15))
        .email(uniqueEmail(15))
        .count(1)
        .timestamp(Instant.parse("2024-11-15T10:00:00Z"))
        .build()

    def decemberEvent = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(16))
        .email(uniqueEmail(16))
        .count(1)
        .timestamp(Instant.parse("2024-12-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2024, 11, 30)

    insertEventLog(novemberEvent, "/2nd-pillar-flow", Instant.parse("2024-11-15T10:00:00Z"))
    insertUnitOwner(novemberEvent, snapshotDate)

    insertEventLog(decemberEvent, "/2nd-pillar-flow", Instant.parse("2024-12-15T10:00:00Z"))
    insertUnitOwner(decemberEvent, snapshotDate)

    when:
    def results = repository.fetch(LocalDate.parse("2024-11-01"), LocalDate.parse("2024-12-31"))

    then:
    results.size() == 1
    results.first().personalCode() == novemberEvent.personalCode()
  }

  def "uses latest non-December snapshot"() {
    given:
    def abandonment = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(17))
        .email(uniqueEmail(17))
        .count(1)
        .timestamp(Instant.parse("2024-11-15T10:00:00Z"))
        .build()

    def novemberSnapshotDate = LocalDate.of(2024, 11, 30)
    def decemberSnapshotDate = LocalDate.of(2024, 12, 31)

    insertEventLog(abandonment, "/2nd-pillar-flow", Instant.parse("2024-11-15T10:00:00Z"))

    // Insert older November snapshot
    insertUnitOwner(abandonment, novemberSnapshotDate)

    // Insert newer December snapshot with different data
    def decemberAbandonment = aSecondPillarAbandonment()
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

  def "excludes users with p2_rava_status = 'R'"() {
    given:
    def withoutRavaStatus = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(18))
        .email(uniqueEmail(18))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def withRavaStatusR = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(19))
        .email(uniqueEmail(19))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(withoutRavaStatus, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withoutRavaStatus, snapshotDate)

    insertEventLog(withRavaStatusR, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withRavaStatusR, snapshotDate, "R", null)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == withoutRavaStatus.personalCode()
  }

  def "includes users with p2_rava_status other than 'R'"() {
    given:
    def withRavaStatusNull = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(20))
        .email(uniqueEmail(20))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def withRavaStatusA = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(21))
        .email(uniqueEmail(21))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def withRavaStatusP = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(22))
        .email(uniqueEmail(22))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)

    insertEventLog(withRavaStatusNull, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withRavaStatusNull, snapshotDate)

    insertEventLog(withRavaStatusA, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withRavaStatusA, snapshotDate, "A", null)

    insertEventLog(withRavaStatusP, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withRavaStatusP, snapshotDate, "P", null)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 3
    results.collect { it.personalCode() }.sort() ==
        [withRavaStatusNull.personalCode(), withRavaStatusA.personalCode(), withRavaStatusP.personalCode()].sort()
  }

  def "excludes users with p2_duty_end set"() {
    given:
    def withoutDutyEnd = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(23))
        .email(uniqueEmail(23))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def withDutyEnd = aSecondPillarAbandonment()
        .personalCode(uniquePersonalCode(24))
        .email(uniqueEmail(24))
        .count(1)
        .timestamp(Instant.parse("2025-01-15T10:00:00Z"))
        .build()

    def snapshotDate = LocalDate.of(2025, 1, 31)
    def dutyEndDate = LocalDate.of(2025, 2, 15)

    insertEventLog(withoutDutyEnd, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withoutDutyEnd, snapshotDate)

    insertEventLog(withDutyEnd, "/2nd-pillar-flow", Instant.parse("2025-01-15T10:00:00Z"))
    insertUnitOwner(withDutyEnd, snapshotDate, null, dutyEndDate)

    when:
    def results = repository.fetch(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-02-01"))

    then:
    results.size() == 1
    results.first().personalCode() == withoutDutyEnd.personalCode()
  }


  private void insertEventLog(SecondPillarAbandonment abandonment, String path, Instant timestamp) {
    jdbcClient.sql("""
            INSERT INTO event_log (principal, type, data, timestamp)
            VALUES (:principal, :type, :data::jsonb, :timestamp)""")
        .param("principal", abandonment.personalCode())
        .param("type", "PAGE_VIEW")
        .param("data", "{\"path\": \"${path}\"}")
        .param("timestamp", Timestamp.from(timestamp))
        .update()
  }

  private void insertUnitOwner(SecondPillarAbandonment abandonment, LocalDate snapshotDate,
                                String p2RavaStatus = null, LocalDate p2DutyEnd = null) {
    jdbcClient.sql("""
            INSERT INTO unit_owner
              (personal_id, first_name, last_name, email, language_preference,
               p2_rate, p2_next_rate, p2_next_rate_date, snapshot_date, date_created,
               p2_rava_status, p2_duty_end)
            VALUES
              (:personal_id, :first_name, :last_name, :email, :language_preference,
               :p2_rate::INTEGER, :p2_next_rate::INTEGER, :p2_next_rate_date, :snapshot_date, :date_created,
               :p2_rava_status, :p2_duty_end)""")
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
        .param("p2_rava_status", p2RavaStatus)
        .param("p2_duty_end", p2DutyEnd)
        .update()
  }

  private void insertEmail(SecondPillarAbandonment abandonment) {
    insertEmail(abandonment, "SECOND_PILLAR_ABANDONMENT", abandonment.lastEmailSentDate())
  }

  private void insertEmail(SecondPillarAbandonment abandonment, String type, Instant createdDate) {
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
