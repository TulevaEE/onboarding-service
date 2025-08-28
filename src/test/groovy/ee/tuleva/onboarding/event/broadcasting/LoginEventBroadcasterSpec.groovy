package ee.tuleva.onboarding.event.broadcasting

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.contact.ContactDetailsService
import ee.tuleva.onboarding.event.TrackableEvent
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticationTokensFixture.sampleAuthenticationTokens
import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.*
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.sampleSmartIdSession
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.event.TrackableEventType.LOGIN

class LoginEventBroadcasterSpec extends Specification {

  ApplicationEventPublisher eventPublisher = Mock()
  UserConversionService conversionService = Mock()
  ContactDetailsService contactDetailsService = Mock()
  ConversionDecorator conversionDecorator = Mock()
  GrantedAuthorityFactory grantedAuthorityFactory = Mock()
  SecondPillarPaymentRateService secondPillarPaymentRateService = Mock()

  LoginEventBroadcaster service = new LoginEventBroadcaster(eventPublisher, conversionService, contactDetailsService,
      conversionDecorator, grantedAuthorityFactory, secondPillarPaymentRateService)

  def "OnAfterTokenGrantedEvent: Broadcast login event"() {
    given:
    def samplePerson = sampleAuthenticatedPersonAndMember()
    if (document != null) {
      samplePerson.attributes([(ID_DOCUMENT_TYPE): document.name()])
    }
    samplePerson = samplePerson.build()
    def tokens = sampleAuthenticationTokens()

    def event = new AfterTokenGrantedEvent(this, samplePerson, grantType, tokens)

    when:
    service.onAfterTokenGrantedEvent(event)

    then:
    if (document != null) {
      1 * eventPublisher.publishEvent(new TrackableEvent(samplePerson, LOGIN, [method: grantType, document: document, idDocumentType: document.name()]))
    } else {
      1 * eventPublisher.publishEvent(new TrackableEvent(samplePerson, LOGIN, [method: grantType]))
    }

    where:
    grantType | document                 | credentials
    ID_CARD   | DIGITAL_ID_CARD          | new IdCardSession("Chuck", "Norris", "38512121212", DIGITAL_ID_CARD)
    ID_CARD   | OLD_ID_CARD              | new IdCardSession("Chuck", "Norris", "38512121212", OLD_ID_CARD)
    ID_CARD   | ESTONIAN_CITIZEN_ID_CARD | new IdCardSession("Chuck", "Norris", "38512121212", ESTONIAN_CITIZEN_ID_CARD)
    ID_CARD   | DIPLOMATIC_ID_CARD       | new IdCardSession(" Chuck ", " Norris ", " 38512121212 ", DIPLOMATIC_ID_CARD)
    MOBILE_ID | null                     | sampleMobileIdSession
    SMART_ID  | null                     | sampleSmartIdSession
  }

  def "OnAfterTokenGrantedEvent: add conversion metadata"() {
    given:
    def samplePerson = sampleAuthenticatedPersonAndMember().build()
    def tokens = sampleAuthenticationTokens()
    def contactDetails = contactDetailsFixture()
    def conversion = fullyConverted()

    def event = new AfterTokenGrantedEvent(this, samplePerson, SMART_ID, tokens)

    1 * conversionService.getConversion(samplePerson) >> conversion
    1 * contactDetailsService.getContactDetails(samplePerson) >> contactDetails
    1 * secondPillarPaymentRateService.getPaymentRates(samplePerson) >> new PaymentRates(4, null)
    1 * conversionDecorator.addConversionMetadata(_, conversion, contactDetails, samplePerson, _) >> {
      (data) -> data.sample = "conversion"
    }

    when:
    service.onAfterTokenGrantedEvent(event)

    then:
    1 * eventPublisher.publishEvent(new TrackableEvent(samplePerson, LOGIN, [method: SMART_ID]))
  }
}
