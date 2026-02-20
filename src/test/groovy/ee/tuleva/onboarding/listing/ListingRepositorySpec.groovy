package ee.tuleva.onboarding.listing

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import spock.lang.Specification

import static ee.tuleva.onboarding.listing.ListingsFixture.*
import static ee.tuleva.onboarding.time.TestClockHolder.now

@DataJpaTest
class ListingRepositorySpec extends Specification {

  @Autowired
  ListingRepository repository

  def "findByExpiryTimeAfter returns only active listings"() {
    given:
    def active = repository.save(activeListing().id(null).build())
    expect:
    repository.findByExpiryTimeAfter(now) == [active]
  }

  def "can find a listing by id and member id"() {
    given:
    def listing = repository.save(activeListing().id(null).build())

    when:
    def found = repository.findByIdAndMemberId(listing.id, listing.memberId).orElseThrow()

    then:
    found == listing
  }
}
