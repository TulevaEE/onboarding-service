package ee.tuleva.onboarding.listing


import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.listing.ListingType.SELL
import static ee.tuleva.onboarding.listing.ListingsFixture.activeListing
import static ee.tuleva.onboarding.listing.ListingsFixture.newListingRequest

class ListingServiceSpec extends Specification {

  ListingRepository listingRepository = Mock()
  UserService userService = Mock()
  Clock clock = TestClockHolder.clock
  ListingService service = new ListingService(listingRepository, userService, clock)

  def 'createListing maps request, saves entity, and returns DTO'() {
    given:
    def request = newListingRequest().build()
    def person = sampleAuthenticatedPersonAndMember().build()

    def savedListing = request.toListing(42L).tap {
      id = 1L
      createdTime = Instant.now()
    }
    listingRepository.save(_ as Listing) >> savedListing
    userService.getById(person.userId) >> sampleUser().build()

    when:
    def createdListing = service.createListing(request, person)

    then:
    createdListing == ListingDto.from(savedListing)
  }

  def 'findActiveListings retrieves and maps active listings'() {
    given:
    def entity = activeListing()
        .id(1L)
        .memberId(42L)
        .type(SELL)
        .build()
    listingRepository.findByExpiryTimeAfter(clock.instant()) >> [entity]

    when:
    def results = service.findActiveListings()

    then:
    results == [ListingDto.from(entity)]
  }

  def "deleteListing delegates to repository"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def user = sampleUser().build()
    userService.getById(authenticatedPerson.userId) >> user

    when:
    service.deleteListing(1L, authenticatedPerson)

    then:
    1 * listingRepository.deleteByIdAndMemberId(1L, user.memberId)
  }
}
