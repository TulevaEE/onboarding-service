package ee.tuleva.onboarding.analytics


import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.user.address.AddressFixture.getAnAddress

@DataJpaTest
class AnalyticsThirdPillarRepositorySpec extends Specification {
  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private AnalyticsThirdPillarRepository repository

  def "persisting and findById() works"() {
    given:
    User sampleUser = sampleUser().build()

    AnalyticsThirdPillar analyticsThirdPillar = AnalyticsThirdPillar.builder()
    .personalCode(sampleUser.personalCode)
       .firstName(sampleUser.firstName)
    .lastName(sampleUser.lastName)
    .email(sampleUser.email)
    .phoneNo(sampleUser.phoneNumber)
    .country(anAddress.countryCode)
    .reportingDate(LocalDate.now())
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

  def "persisting and findAllByReportingDate() works"() {
    given:
    User sampleUser = sampleUser().build()

    AnalyticsThirdPillar analyticsThirdPillar = AnalyticsThirdPillar.builder()
        .personalCode(sampleUser.personalCode)
        .firstName(sampleUser.firstName)
        .lastName(sampleUser.lastName)
        .email(sampleUser.email)
        .phoneNo(sampleUser.phoneNumber)
        .country(anAddress.countryCode)
        .reportingDate(LocalDate.now())
        .dateCreated(LocalDateTime.now())
        .build()

    AnalyticsThirdPillar persistedEntity = entityManager.persist(analyticsThirdPillar)

    entityManager.flush()

    when:
    List<AnalyticsThirdPillar> records = repository.findAllByReportingDate(LocalDate.now())

    then:
    records == [persistedEntity]
  }

  def "persisting and findAllWithMostRecentReportingDate() works"() {
    given:
    User sampleUser = sampleUser().build()

    AnalyticsThirdPillar analyticsThirdPillar = AnalyticsThirdPillar.builder()
        .personalCode(sampleUser.personalCode)
        .firstName(sampleUser.firstName)
        .lastName(sampleUser.lastName)
        .email(sampleUser.email)
        .phoneNo(sampleUser.phoneNumber)
        .country(anAddress.countryCode)
        .reportingDate(LocalDate.now())
        .dateCreated(LocalDateTime.now())
        .build()

    AnalyticsThirdPillar persistedEntity = entityManager.persist(analyticsThirdPillar)

    entityManager.flush()

    when:
    List<AnalyticsThirdPillar> records = repository.findAllWithMostRecentReportingDate()

    then:
    records == [persistedEntity]
  }
}
