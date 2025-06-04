package ee.tuleva.onboarding.listing

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import spock.lang.Specification

import java.time.Instant

import static ee.tuleva.onboarding.listing.ListingsFixture.activeListing
import static ee.tuleva.onboarding.listing.ListingsFixture.expiredListing

@DataJpaTest
class ListingRepositorySpec extends Specification {

  @Autowired
  ListingRepository repository

  def "findByExpiryTimeAfter returns only active listings"() {
    given:
    def active = repository.save(activeListing().build())
    repository.save(expiredListing().build())

    expect:
    repository.findByExpiryTimeAfter(Instant.now()) == [active]
  }
}
