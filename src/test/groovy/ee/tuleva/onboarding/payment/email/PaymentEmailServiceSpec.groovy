package ee.tuleva.onboarding.payment.email

import com.microtripit.mandrillapp.lutung.view.MandrillMessage
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.email.PillarSuggestion
import ee.tuleva.onboarding.mandate.email.persistence.Email
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService
import ee.tuleva.onboarding.notification.email.EmailService
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notConverted
import static ee.tuleva.onboarding.currency.Currency.EUR
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getAttachments
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.*
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
    def conversion = notConverted()
    def contactDetails = contactDetailsFixture()

    def paymentRates = samplePaymentRates()
    def pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates)
    def payment = aNewSinglePayment()
    def message = new MandrillMessage()
    var mergeVars = [
        "fname"                : user.firstName,
        "lname"                : user.lastName,
        "amount"               : 10.00,
        "currency"             : EUR,
        "senderPersonalCode"   : user.personalCode,
        "recipientPersonalCode": payment.recipientPersonalCode,
        "suggestPaymentRate"   : pillarSuggestion.suggestPaymentRate,
        "suggestMembership"    : pillarSuggestion.suggestMembership,
        "suggestSecondPillar"  : pillarSuggestion.suggestSecondPillar,
        "suggestThirdPillar"   : pillarSuggestion.suggestThirdPillar
    ]
    def tags = ["pillar_3.1", "mandate", "payment", "suggest_payment_rate", "suggest_2"]
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
      it.size() == 1 && it.first.getName() == mandateAttachments.first.getName() && it.first.getContent() == mandateAttachments.first.getContent()
    }) >> message
    1 * emailService.send(user, message, "third_pillar_payment_success_mandate_en") >> Optional.of(mandrillResponse)
    1 * emailPersistenceService.save(user, mandrillResponse.id, THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, mandrillResponse.status)
  }

  def "send savings fund payment emails"() {
    given:
    def user = sampleUser().build()
    def message = new MandrillMessage()
    var mergeVars = ["fname": user.firstName]
    def tags = ["savings_fund"]
    def locale = Locale.ENGLISH

    def mandrillResponse = new MandrillMessageStatus().tap {
      _id = "123"
      status = "sent"
    }

    when:
    paymentEmailService.sendSavingsFundPaymentEmail(user, emailType, locale)

    then:
    1 * emailService.send(user, message, templateName) >> Optional.of(mandrillResponse)
    1 * emailService.newMandrillMessage(user.email, templateName, mergeVars, tags, null) >> message
    1 * emailPersistenceService.save(user, mandrillResponse.id, emailType, mandrillResponse.status)

    where:
    emailType                    | templateName
    SAVINGS_FUND_PAYMENT_SUCCESS | "savings_fund_payment_success_en"
    SAVINGS_FUND_PAYMENT_FAIL    | "savings_fund_payment_failed_en"
    SAVINGS_FUND_PAYMENT_CANCEL  | "savings_fund_payment_cancelled_en"
  }
}
