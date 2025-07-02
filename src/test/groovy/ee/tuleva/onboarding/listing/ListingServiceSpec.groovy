package ee.tuleva.onboarding.listing


import ee.tuleva.onboarding.capital.ApiCapitalEvent
import ee.tuleva.onboarding.capital.CapitalService
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.time.ClockHolder
import ee.tuleva.onboarding.time.TestClockHolder
import ee.tuleva.onboarding.user.UserService
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT
import static ee.tuleva.onboarding.listing.Listing.State.CANCELLED
import static ee.tuleva.onboarding.listing.ListingType.SELL
import static ee.tuleva.onboarding.listing.ListingsFixture.*

class ListingServiceSpec extends Specification {

  ListingRepository listingRepository = Mock()
  UserService userService = Mock()
  Clock clock = TestClockHolder.clock
  LocaleService localeService = Mock()
  CapitalService capitalService = Mock()
  ListingService service = new ListingService(listingRepository, userService, clock, capitalService, localeService)

  def setup() {
    ClockHolder.setClock(TestClockHolder.clock)
  }

  def cleanup() {
    ClockHolder.setDefaultClock()
  }

  def "createListing maps request, saves entity, and returns DTO"() {
    given:
    def user = sampleUser().build()
    def request = newListingRequest().build()
    def person = authenticatedPersonFromUser(user).build()

    def savedListing = request.toListing(42L, 'et').tap {
      id = 1L
      createdTime = Instant.now()
    }
    listingRepository.save(_ as Listing) >> savedListing
    userService.getById(person.userId) >> user
    capitalService.getCapitalEvents(_) >> List.of(new ApiCapitalEvent(LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000),  Currency.EUR))

    when:
    def createdListing = service.createListing(request, person)

    then:
    createdListing == ListingDto.from(savedListing, user)
  }

  def "findActiveListings retrieves and maps active listings"() {
    given:
    def entity = activeListing()
        .id(1L)
        .memberId(42L)
        .type(SELL)
        .build()
    listingRepository.findByExpiryTimeAfter(clock.instant()) >> [entity]

    var person = sampleAuthenticatedPersonAndMember()
    userService.getById(_) >> sampleUser().build()
    when:
    def results = service.findActiveListings(person.build())

    then:
    results == [ListingDto.from(entity, sampleUser().build())]
  }

  def "can cancel active listings"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def user = sampleUser().build()
    userService.getById(authenticatedPerson.userId) >> user
    def listing = activeListing().id(1L).memberId(user.memberId).build()
    1 * listingRepository.findByIdAndMemberId(1L, user.memberId) >> Optional.of(listing)
    1 * listingRepository.save(_) >> { Listing it ->
      it.tap {
        state = CANCELLED
        cancelledTime = clock.instant()
      }
    }

    when:
    def returnedListing = service.cancelListing(1L, authenticatedPerson)

    then:
    returnedListing.state == CANCELLED
    returnedListing.cancelledTime == clock.instant()
  }

  def "can not cancel listings that are in other states"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def user = sampleUser().build()
    userService.getById(authenticatedPerson.userId) >> user
    def listing = expiredListing().id(1L).memberId(user.memberId).build()
    1 * listingRepository.findByIdAndMemberId(1L, user.memberId) >> Optional.of(listing)

    when:
    service.cancelListing(1L, authenticatedPerson)

    then:
    thrown(IllegalStateException)
  }
}
