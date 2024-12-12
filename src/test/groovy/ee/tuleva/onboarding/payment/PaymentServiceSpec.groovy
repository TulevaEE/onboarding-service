package ee.tuleva.onboarding.payment

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.contact.ContactDetailsFixture
import ee.tuleva.onboarding.mandate.event.BeforePaymentLinkCreatedEvent
import ee.tuleva.onboarding.payment.provider.montonio.MontonioCallbackService
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentLinkGenerator
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.RECURRING
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SINGLE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewMemberPayment
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewMemberPaymentForExistingMember
import static ee.tuleva.onboarding.payment.PaymentFixture.aPaymentData
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedSinglePaymentFinishedToken
import static ee.tuleva.onboarding.payment.provider.PaymentProviderFixture.aSerializedMemberPaymentFinishedToken

class PaymentServiceSpec extends Specification {

  PaymentRepository paymentRepository = Mock()
  SinglePaymentLinkGenerator singlePaymentLinkGenerator = Mock()
  RecurringPaymentLinkGenerator recurringPaymentLinkGenerator = Mock()
  MontonioCallbackService paymentProviderCallbackService = Mock()
  UserService userService = Mock()
  ApplicationEventPublisher eventPublisher = Mock()
  EpisService episService = Mock()

  PaymentService paymentService = new PaymentService(
      paymentRepository, singlePaymentLinkGenerator, recurringPaymentLinkGenerator, paymentProviderCallbackService, userService, eventPublisher, episService)

  def "can get payments"() {
    given:
    def person = samplePerson()
    def payment = aNewSinglePayment()
    paymentRepository.findAllByRecipientPersonalCodeAndPaymentTypeNot(person.personalCode, MEMBER_FEE) >> [payment]
    when:
    def payments = paymentService.getThirdPillarPayments(person)
    then:
    payments == [payment]
  }

  def "can get a single payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { type = SINGLE }
    def sampleUser = sampleUser().build()
    def contactDetails = contactDetailsFixture()
    def link = new PaymentLink("https://single.payment.url")

    singlePaymentLinkGenerator.getPaymentLink(paymentData, person) >> link
    episService.getContactDetails(person) >> contactDetails
    userService.findByPersonalCode(person.getPersonalCode()) >> Optional.of(sampleUser)

    when:
    def returnedLink = paymentService.getLink(paymentData, person)

    then:
    1 * eventPublisher.publishEvent(_ as BeforePaymentLinkCreatedEvent)
    returnedLink == link
  }

  def "can get a member payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { type = MEMBER_FEE }
    def sampleUser = sampleUser().build()
    def contactDetails = contactDetailsFixture()
    def link = new PaymentLink("https://member.payment.url")

    singlePaymentLinkGenerator.getPaymentLink(paymentData, person) >> link
    episService.getContactDetails(person) >> contactDetails
    userService.findByPersonalCode(person.getPersonalCode()) >> Optional.of(sampleUser)

    when:
    def returnedLink = paymentService.getLink(paymentData, person)

    then:
    1 * eventPublisher.publishEvent(_ as BeforePaymentLinkCreatedEvent)
    returnedLink == link
  }

  def "can get a recurring payment link"() {
    given:
    def person = samplePerson
    def paymentData = aPaymentData().tap { type = RECURRING }
    def sampleUser = sampleUser().build()
    def contactDetails = contactDetailsFixture()
    def link = new PaymentLink("https://recurring.payment.url")

    recurringPaymentLinkGenerator.getPaymentLink(paymentData, person) >> link
    singlePaymentLinkGenerator.getPaymentLink(paymentData, person) >> link
    episService.getContactDetails(person) >> contactDetails
    userService.findByPersonalCode(person.getPersonalCode()) >> Optional.of(sampleUser)

    when:
    def returnedLink = paymentService.getLink(paymentData, person)

    then:
    1 * eventPublisher.publishEvent(_ as BeforePaymentLinkCreatedEvent)
    returnedLink == link
  }

  def "can process a payment provider token"() {
    given:
    def token = aSerializedSinglePaymentFinishedToken
    def payment = Optional.of(aNewSinglePayment())
    paymentProviderCallbackService.processToken(token) >> payment

    when:
    def returnedPayment = paymentService.processToken(token)

    then:
    returnedPayment == payment
  }

  def "can process a member payment confirmation token and register a new member"() {
    given:
    def token = aSerializedMemberPaymentFinishedToken
    def payment = aNewMemberPayment()
    def optionalPayment = Optional.of(payment)
    paymentProviderCallbackService.processToken(token) >> optionalPayment
    1 * userService.registerAsMember(payment.user.id)

    when:
    def returnedPayment = paymentService.processToken(token)

    then:
    returnedPayment == optionalPayment
  }

  def "can process a member payment confirmation token and do not register an already member"() {
    given:
    def token = aSerializedMemberPaymentFinishedToken
    def paymentOptional = Optional.of(aNewMemberPaymentForExistingMember())
    paymentProviderCallbackService.processToken(token) >> paymentOptional
    0 * userService.registerAsMember(_)

    when:
    def returnedPayment = paymentService.processToken(token)

    then:
    returnedPayment == paymentOptional
  }


}
