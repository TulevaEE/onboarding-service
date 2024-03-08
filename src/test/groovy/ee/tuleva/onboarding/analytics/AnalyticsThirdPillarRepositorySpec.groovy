package ee.tuleva.onboarding.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.aml.AmlCheck
import ee.tuleva.onboarding.aml.AmlCheckRepository
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.address.AddressFixture
import ee.tuleva.onboarding.user.address.AddressValidatorSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.LocalDateTime

import static ee.tuleva.onboarding.aml.AmlCheckType.DOCUMENT
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.user.address.AddressFixture.*
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

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
}
