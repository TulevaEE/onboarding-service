package ee.tuleva.onboarding.payment.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.currency.Currency
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments
import ee.tuleva.onboarding.mandate.email.PillarSuggestion
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getAttachments
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE
import static ee.tuleva.onboarding.payment.PaymentFixture.aNewSinglePayment
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates

class PaymentEmailServiceSpec extends Specification {

  EmailService emailService = Mock()
  EmailPersistenceService emailPersistenceService = Mock()

  PaymentEmailService paymentEmailService = new PaymentEmailService(emailService,
      emailPersistenceService)

  def "send third pillar payment success email"() {
    given:
    def user = sampleUser().build()
    def conversion = notFullyConverted()
    def contactDetails = contactDetailsFixture()
    /*contactDetails.setThirdPillarActive(true)
    contactDetails.setSecondPillarActive(false)*/

    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def payment = aNewSinglePayment()
    def message = new MandrillMessage()
    HashMap<String, Object> mergeVars = [
        "fname"              : user.firstName,
        "lname"              : user.lastName,
        "amount"             : 10.00,
        "currency"           : EUR,
        "recipient"          : payment.recipientPersonalCode,
        "suggestPaymentRate" : pillarSuggestion.suggestPaymentRate,
        "suggestMembership"  : pillarSuggestion.suggestMembership,
        "suggestSecondPillar": pillarSuggestion.suggestSecondPillar,
        "suggestThirdPillar": pillarSuggestion.suggestThirdPillar
    ]
    def tags = ["pillar_3.1", "mandate", "payment", "suggest_payment_rate"]
    def locale = Locale.ENGLISH
    def mandrillMessageId = "mandrillMessageId123"
    def mandate = new Mandate(mandate: new byte[0])
    def mandateAttachments = getAttachments(user, mandate)

    emailPersistenceService.cancel(user, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE) >> [new Email(
        personalCode: user.personalCode, mandrillMessageId: mandrillMessageId,
        type: THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, mandate: mandate
    )]
    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendThirdPillarPaymentSuccessEmail(user, payment, pillarSuggestion, locale)

    then:
    1 * emailService.newMandrillMessage(user.email, "third_pillar_payment_success_mandate_en", mergeVars, tags, {
      it.size() == 1 && it.first.getName() == mandateAttachments.first.getName()
    }) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, mandrillResponse.status)
  }
}
