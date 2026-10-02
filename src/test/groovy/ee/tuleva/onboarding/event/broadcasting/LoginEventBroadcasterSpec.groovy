package ee.tuleva.onboarding.event.broadcasting

import ee.tuleva.onboarding.auth.ClientConnection
import ee.tuleva.onboarding.auth.SecurityContextRunner
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent
import ee.tuleva.onboarding.auth.idcard.IdCardSession
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.event.PillarActivation
import ee.tuleva.onboarding.event.PillarActivations
import ee.tuleva.onboarding.event.TrackableEvent
import ee.tuleva.onboarding.conversion.ConversionDecorator
import ee.tuleva.onboarding.paymentrate.PaymentRates
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember
import static ee.tuleva.onboarding.auth.AuthenticationTokensFixture.sampleAuthenticationTokens
import static ee.tuleva.onboarding.auth.GrantType.*
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.*
import static ee.tuleva.onboarding.auth.principal.AuthenticatedPerson.SMART_ID_DOCUMENT_NUMBER
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.event.TrackableEventType.LOGIN

class LoginEventBroadcasterSpec extends Specification {

  def setup() {
    RequestContextHolder.resetRequestAttributes()
  }

  def cleanup() {
    RequestContextHolder.resetRequestAttributes()
  }

  ApplicationEventPublisher eventPublisher = Mock()
  UserConversionService conversionService = Mock()
  PillarActivations pillarActivations = Mock()
  ConversionDecorator conversionDecorator = Mock()
  SecurityContextRunner securityContextRunner = Mock() {
    runAs(_, _, _) >> { args -> (args[2] as Runnable).run() }
  }
  SecondPillarPaymentRateService secondPillarPaymentRateService = Mock()
  ClientConnection clientConnection = new ClientConnection()

  LoginEventBroadcaster service = new LoginEventBroadcaster(eventPublisher, conversionService, pillarActivations,
      conversionDecorator, securityContextRunner, secondPillarPaymentRateService, clientConnection)

  def "OnAfterTokenGrantedEvent: Broadcast login event"() {
    given:
    def samplePerson = sampleAuthenticatedPersonAndMember()
    if (document != null) {
      samplePerson.attributes([(ID_DOCUMENT_TYPE): document.name()])
    }
    samplePerson = samplePerson.build()
    def tokens = sampleAuthenticationTokens()

    def event = new AfterTokenGrantedEvent(this, samplePerson, grantType, tokens)

    pillarActivations.forPerson(_) >> new PillarActivation(true, true)

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
    SMART_ID  | null                     | aDeviceLinkSession(java.time.Instant.EPOCH)
  }

  def "OnAfterTokenGrantedEvent: add conversion metadata"() {
    given:
    def samplePerson = sampleAuthenticatedPersonAndMember().build()
    def tokens = sampleAuthenticationTokens()
    def contactDetails = contactDetailsFixture()
    def conversion = fullyConverted()

    def event = new AfterTokenGrantedEvent(this, samplePerson, SMART_ID, tokens)

    1 * conversionService.getConversion(samplePerson) >> conversion
    1 * pillarActivations.forPerson(samplePerson) >> new PillarActivation(contactDetails.secondPillarActive, contactDetails.thirdPillarActive)
    1 * secondPillarPaymentRateService.getPaymentRates(samplePerson) >> new PaymentRates(4, null)
    1 * conversionDecorator.addConversionMetadata(_, conversion, contactDetails.secondPillarActive, contactDetails.thirdPillarActive, samplePerson, _) >> {
      (data) -> data.sample = "conversion"
    }

    when:
    service.onAfterTokenGrantedEvent(event)

    then:
    1 * eventPublisher.publishEvent(new TrackableEvent(samplePerson, LOGIN, [method: SMART_ID]))
  }

  def "keeps the Smart-ID document number out of the login event"() {
    given:
    def person = sampleAuthenticatedPersonAndMember()
        .attributes([(GRANT_TYPE): SMART_ID.name(), (SMART_ID_DOCUMENT_NUMBER): "PNOEE-38888888888-MOCK-Q"])
        .build()
    def event = new AfterTokenGrantedEvent(this, person, SMART_ID, sampleAuthenticationTokens())
    pillarActivations.forPerson(_) >> new PillarActivation(true, true)

    when:
    service.onAfterTokenGrantedEvent(event)

    then:
    1 * eventPublisher.publishEvent(new TrackableEvent(person, LOGIN, [method: SMART_ID, (GRANT_TYPE): SMART_ID.name()]))
  }
}
