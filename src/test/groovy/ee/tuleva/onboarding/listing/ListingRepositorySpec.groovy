package ee.tuleva.onboarding.listing


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import spock.lang.Specification

import static ee.tuleva.onboarding.listing.ListingsFixture.activeListing
import static ee.tuleva.onboarding.listing.ListingsFixture.expiredListing
import static ee.tuleva.onboarding.time.TestClockHolder.now

@DataJpaTest
class ListingRepositorySpec extends Specification {

  @Autowired
  ListingRepository repository

  def "findByExpiryTimeAfter returns only active listings"() {
    given:
    def active = repository.save(activeListing().id(null).build())
    repository.save(expiredListing().id(null).build())

    expect:
    repository.findByExpiryTimeAfter(now) == [active]
  }
}
