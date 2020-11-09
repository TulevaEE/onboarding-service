package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.user.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

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
        entityManager.persist(savedUser)
        entityManager.flush()

        def address = addressFixture().build()
        def metadata = ["conversion": true]

        def savedMandate = Mandate.builder()
            .user(savedUser)
            .futureContributionFundIsin("isin")
            .fundTransferExchanges([])
            .pillar(2)
            .address(address)
            .metadata(metadata)
            .build()
        entityManager.persist(savedMandate)
        entityManager.flush()

        when:
        def mandate = repository.findByIdAndUserId(savedMandate.id, savedUser.id)

        then:
        mandate.user == savedUser
        mandate.futureContributionFundIsin == Optional.of("isin")
        mandate.fundTransferExchanges == []
        mandate.address == address
        mandate.metadata == metadata

    }

}