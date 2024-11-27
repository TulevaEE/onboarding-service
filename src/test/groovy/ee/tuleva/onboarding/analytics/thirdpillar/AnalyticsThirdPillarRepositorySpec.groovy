package ee.tuleva.onboarding.analytics.thirdpillar


import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.address.AddressFixture
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static java.time.LocalTime.MAX

@DataJpaTest
class AnalyticsThirdPillarRepositorySpec extends Specification {
  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private AnalyticsThirdPillarRepository repository

  def "findById() works"() {
    given:
    User sampleUser = sampleUser().build()

    AnalyticsThirdPillar analyticsThirdPillar = AnalyticsThirdPillar.builder()
        .personalCode(sampleUser.personalCode)
        .firstName(sampleUser.firstName)
        .lastName(sampleUser.lastName)
        .email(sampleUser.email)
        .phoneNo(sampleUser.phoneNumber)
        .country(AddressFixture.anAddress.countryCode)
        .reportingDate(LocalDateTime.now())
        .dateCreated(LocalDateTime.now())
        .build()

    entityManager.persist(analyticsThirdPillar)

    entityManager.flush()

    when:
    def record = repository.findById(analyticsThirdPillar.id)

    then:
    record.isPresent()
    record.get().id != null
    record.get().personalCode == sampleUser.personalCode
  }

  def "findAllByReportingDate() works"() {
    given:
    User sampleUser = sampleUser().build()

    AnalyticsThirdPillar analyticsThirdPillar = AnalyticsThirdPillar.builder()
        .personalCode(sampleUser.personalCode)
        .firstName(sampleUser.firstName)
        .lastName(sampleUser.lastName)
        .email(sampleUser.email)
        .phoneNo(sampleUser.phoneNumber)
        .country(AddressFixture.anAddress.countryCode)
        .reportingDate(LocalDateTime.now())
        .dateCreated(LocalDateTime.now())
        .build()

    AnalyticsThirdPillar persistedEntity = entityManager.persist(analyticsThirdPillar)

    entityManager.flush()

    when:
    List<AnalyticsThirdPillar> records =
        repository.findAllByReportingDateBetween(LocalDate.now().atStartOfDay(), LocalDate.now().atTime(MAX))

    then:
    records == [persistedEntity]
  }

  def "findAllWithMostRecentReportingDate() works"() {
    given:
    User sampleUser = sampleUser().build()

    AnalyticsThirdPillar analyticsThirdPillar = AnalyticsThirdPillar.builder()
        .personalCode(sampleUser.personalCode)
        .firstName(sampleUser.firstName)
        .lastName(sampleUser.lastName)
        .email(sampleUser.email)
        .phoneNo(sampleUser.phoneNumber)
        .country(AddressFixture.anAddress.countryCode)
        .reportingDate(LocalDateTime.now())
        .dateCreated(LocalDateTime.now())
        .build()

    AnalyticsThirdPillar persistedEntity = entityManager.persist(analyticsThirdPillar)

    entityManager.flush()

    when:
    List<AnalyticsThirdPillar> records = repository.findAllWithMostRecentReportingDate()

    then:
    records == [persistedEntity]
  }

  def "findByDateRangeAndPersonalCode() works"() {
    given:
        User sampleUser = sampleUser().build()
        LocalDateTime startDate = LocalDate.now().minusDays(10).atStartOfDay()
        LocalDateTime endDate = LocalDate.now().plusDays(10).atTime(MAX)

        AnalyticsThirdPillar analyticsThirdPillar1 = AnalyticsThirdPillar.builder()
            .personalCode(sampleUser.personalCode)
            .firstName(sampleUser.firstName)
            .lastName(sampleUser.lastName)
            .email(sampleUser.email)
            .phoneNo(sampleUser.phoneNumber)
            .country(AddressFixture.anAddress.countryCode)
            .reportingDate(LocalDateTime.now().minusDays(5))
            .dateCreated(LocalDateTime.now())
            .build()

        AnalyticsThirdPillar analyticsThirdPillar2 = AnalyticsThirdPillar.builder()
            .personalCode(sampleUser.personalCode)
            .firstName(sampleUser.firstName)
            .lastName(sampleUser.lastName)
            .email(sampleUser.email)
            .phoneNo(sampleUser.phoneNumber)
            .country(AddressFixture.anAddress.countryCode)
            .reportingDate(LocalDateTime.now().minusDays(15)) // Outside range
            .dateCreated(LocalDateTime.now())
            .build()

        AnalyticsThirdPillar analyticsThirdPillar3 = AnalyticsThirdPillar.builder()
            .personalCode("37605030299")
            .firstName("Other")
            .lastName("User")
            .email("other@example.com")
            .phoneNo("1234567890")
            .country(AddressFixture.anAddress.countryCode)
            .reportingDate(LocalDateTime.now().minusDays(5))
            .dateCreated(LocalDateTime.now())
            .build()

        entityManager.persist(analyticsThirdPillar1)
        entityManager.persist(analyticsThirdPillar2)
        entityManager.persist(analyticsThirdPillar3)
        entityManager.flush()

    when:
        List<AnalyticsThirdPillar> records = repository.findByDateRangeAndPersonalCode(
            startDate, endDate, sampleUser.personalCode
        )

    then:
        records.size() == 1
        records[0].id == analyticsThirdPillar1.id
        records[0].personalCode == sampleUser.personalCode
  }
}
