package ee.tuleva.onboarding.listing

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.capital.ApiCapitalEvent
import ee.tuleva.onboarding.capital.CapitalService
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.locale.LocaleService
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
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
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.UNVESTED_WORK_COMPENSATION
import static ee.tuleva.onboarding.listing.Listing.State.CANCELLED
import static ee.tuleva.onboarding.listing.ListingType.SELL
import static ee.tuleva.onboarding.listing.ListingsFixture.*
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.LISTING_CONTACT

class ListingServiceSpec extends Specification {

  ListingRepository listingRepository = Mock()
  UserService userService = Mock()
  Clock clock = TestClockHolder.clock
  LocaleService localeService = Mock()
  CapitalService capitalService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()
  EmailService emailService = Mock()
  ListingService service = new ListingService(listingRepository, userService, emailPersistenceService, emailService, clock, capitalService, localeService)

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
    userService.getById(person.userId) >> Optional.of(user)
    capitalService.getCapitalEvents(user.getMemberId()) >> List.of(new ApiCapitalEvent(LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000),  Currency.EUR))

    when:
    def createdListing = service.createListing(request, person)

    then:
    createdListing == ListingDto.from(savedListing, user)
  }

  def "createListing does not create listing when not enough member capital"() {
    given:
    def user = sampleUser().build()
    def request = newListingRequest().type(SELL).units(1000000.00).build()
    def person = authenticatedPersonFromUser(user).build()

    def savedListing = request.toListing(42L, 'et').tap {
      id = 1L
      createdTime = Instant.now()
    }
    listingRepository.save(_ as Listing) >> savedListing
    userService.getById(person.userId) >> Optional.of(user)
    capitalService.getCapitalEvents(user.getMemberId()) >> List.of(new ApiCapitalEvent(LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000),  Currency.EUR))

    when:
    service.createListing(request, person)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "Not enough member capital to create listing"
  }

  def "createListing does not consider unvested work compensation as sellable member capital"() {
    given:
    def user = sampleUser().build()
    def request = newListingRequest().type(SELL).units(100.00).build()
    def person = authenticatedPersonFromUser(user).build()

    def savedListing = request.toListing(42L, 'et').tap {
      id = 1L
      createdTime = Instant.now()
    }
    listingRepository.save(_ as Listing) >> savedListing
    userService.getById(person.userId) >> Optional.of(user)
    capitalService.getCapitalEvents(user.getMemberId()) >> List.of(new ApiCapitalEvent(LocalDate.now(clock), UNVESTED_WORK_COMPENSATION, BigDecimal.valueOf(1000),  Currency.EUR))

    when:
    service.createListing(request, person)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "Not enough member capital to create listing"
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
    userService.getById(_) >> Optional.of(sampleUser().build())
    when:
    def results = service.findActiveListings(person.build())

    then:
    results == [ListingDto.from(entity, sampleUser().build())]
  }

  def "can cancel active listings"() {
    given:
    def authenticatedPerson = sampleAuthenticatedPersonAndMember().build()
    def user = sampleUser().build()
    userService.getById(authenticatedPerson.userId) >> Optional.of(user)
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
    userService.getById(authenticatedPerson.userId) >> Optional.of(user)
    def listing = expiredListing().id(1L).memberId(user.memberId).build()
    1 * listingRepository.findByIdAndMemberId(1L, user.memberId) >> Optional.of(listing)

    when:
    service.cancelListing(1L, authenticatedPerson)

    then:
    thrown(IllegalStateException)
  }

  def "can contact listing owner"() {
    given:
    def contacter = sampleUser().build()
    def listingOwner = sampleUser().firstName("Sander").email("sander@tuleva.ee").id(1111).build()
    def contacterPerson = authenticatedPersonFromUser(contacter).build()

    var originalMessage = "Hello!\n I would like to buy your shares.\n Best, Jordan"
    var transformedMessage = "Hello!<br /> I would like to buy your shares.<br /> Best, Jordan"

    def contactMessageRequest = new ContactMessageRequest(originalMessage)

    def savedListing = newListingRequest().type(SELL).units(100.00).build().toListing(42L, 'et').tap {
      id = 1L
      createdTime = Instant.now()
    }

    def mockMessage = Mock(MandrillMessage)

    def messageStatus = Mock(MandrillMessageStatus)
    messageStatus.getStatus() >> "QUEUED"
    messageStatus.getId() >> "ID"
    listingRepository.findById(_) >> Optional.of(savedListing)
    userService.getByMemberId(42L) >> listingOwner
    userService.getById(contacterPerson.userId) >> Optional.of(contacter)
    emailService.newMandrillMessage(
        listingOwner.email,
        contacter.email,
        LISTING_CONTACT.getTemplateName(savedListing.language),
        { Map it ->
          it.get("message") == transformedMessage && it.get("fname") == listingOwner.firstName && it.get("lname") == listingOwner.lastName
        },
        _,
        _
    ) >> mockMessage
    1 * emailService.send(listingOwner, mockMessage, LISTING_CONTACT.getTemplateName(savedListing.language)) >> Optional.of(messageStatus)
    1 * emailPersistenceService.save(listingOwner, "ID", LISTING_CONTACT, "QUEUED") >> Email.builder().id(1).build()
    when:
    def message = service.contactListingOwner(savedListing.id, contactMessageRequest, contacterPerson)

    then:
    message.id() == 1
  }
}
