package ee.tuleva.onboarding.mandate


import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails
import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.pillar.Pillar.SECOND
import static ee.tuleva.onboarding.mandate.MandateType.TRANSFER_CANCELLATION
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

@DataJpaTest
class MandateRepositorySpec extends Specification {

  @Autowired
  private TestEntityManager entityManager

  @Autowired
  private MandateRepository repository

  def "persisting and findByIdAndUserId() works"() {
    given:
    def savedUser = User.builder()
      .firstName("Erko")
      .lastName("Risthein")
      .personalCode("38501010002")
      .email("erko@risthein.ee")
      .phoneNumber("5555555")
      .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
      .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
      .active(true)
      .build()
    entityManager.persistAndFlush(savedUser)

    def address = countryFixture().build()
    def metadata = ["conversion": true]

    def savedMandate = Mandate.builder()
      .user(savedUser)
      .futureContributionFundIsin("isin")
      .pillar(2)
      .details(new TransferCancellationMandateDetails("EE_TEST_ISIN", SECOND))
      .address(address)
      .metadata(metadata)
      .build()
    def fundTransferExchange = FundTransferExchange.builder()
      .sourceFundIsin("AE123232331")
      .targetFundIsin(null)
      .mandate(savedMandate)
      .build()
    savedMandate.fundTransferExchanges = [fundTransferExchange]

    entityManager.persistAndFlush(savedMandate)

    when:
    def mandate = repository.findByIdAndUserId(savedMandate.id, savedUser.id)

    // does not actually check for deserialization, unlike @SpringBootTest
    def castDetails = (TransferCancellationMandateDetails) mandate.details

    then:
    mandate.user == savedUser
    mandate.futureContributionFundIsin == Optional.of("isin")
    mandate.fundTransferExchanges == [fundTransferExchange]
    mandate.address == address
    mandate.metadata == metadata
    castDetails.mandateType == TRANSFER_CANCELLATION
    castDetails.pillar == SECOND
    castDetails.sourceFundIsinOfTransferToCancel == "EE_TEST_ISIN"
  }

}
